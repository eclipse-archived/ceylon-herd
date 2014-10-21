package util;

import java.io.File;

import models.Dependency;
import models.Module;
import models.ModuleVersion;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.exceptions.NoRouteFoundException;

import com.github.rjeschke.txtmark.SpanEmitter;

class MarkdownSpanEmitter implements SpanEmitter {

    static final MarkdownSpanEmitter INSTANCE = new MarkdownSpanEmitter(null);
    
    private static final String DOT_SEPARATOR = ".";
    private static final String PIPE_SEPARATOR = "|";
    private static final String PATH_SEPARATOR = "/";
    private static final String PACKAGE_SEPARATOR = "::";
    private static final String MODULE_DOC_API = "module-doc" + PATH_SEPARATOR + "api";
    
    private final ModuleVersion currentModule;
    
    public MarkdownSpanEmitter(ModuleVersion currentModule) {
        this.currentModule = currentModule;
    }

    @Override
    public void emitSpan(StringBuilder out, String content) {
        if (currentModule == null || StringUtils.isEmpty(content)) {
            printUnresolvableLink(out, content);
            return;
        }        
        
        String namePart = resolveNamePart(content);
        String declPart = resolveDeclPart(content);
        String fileName;
        String anchor;
        String packageName;
        String cssClass = null;
        boolean hasText = content.indexOf(PIPE_SEPARATOR) != -1;
        if(declPart.startsWith("package ")){
            fileName = "index.html";
            anchor = "section-package";
            packageName = declPart.substring(8);
            if(!hasText){
                // skip the "package " prefix
                namePart = packageName;
            }
            cssClass = "packageOrModule";
        }else if(declPart.startsWith("module ")){
            fileName = "index.html";
            anchor = "";
            packageName = declPart.substring(7);
            if(!hasText){
                // skip the "module " prefix
                namePart = packageName;
            }
            cssClass = "packageOrModule";
        }else{
            String declName = resolveDeclName(declPart);
            String[] tuple = resolveFileNameAndAnchor(declName);
            fileName = tuple[0];
            anchor = tuple[1];
            packageName = resolvePackageName(declPart);
            if(fileName.endsWith(".type.html")){
                cssClass = "type-identifier";
            }else if(fileName.equals("index.html")){
                cssClass = "identifier";
            }
        }

        
        ModuleVersion module = resolveModule(packageName);
        String moduleDocUrl = resolveModuleDocUrl(module);
        if (moduleDocUrl == null) {
            printUnresolvableLink(out, content);
            return;
        }
        
        String packagePath = resolvePackagePath(module, packageName);
        if( docFileExists(module, packagePath, fileName) ) {
            printLink(out, namePart, moduleDocUrl, packagePath, fileName, anchor, cssClass, !hasText);
        }
        else {
            // if we did not have a package part then perhaps it's not in this module but in the language module?
            if(!declPart.contains(PACKAGE_SEPARATOR) && !currentModule.module.name.equals("ceylon.language")){
                // try again in the language module
                packageName = "ceylon.language";
                module = resolveModule(packageName);
                moduleDocUrl = resolveModuleDocUrl(module);
                if(moduleDocUrl != null){
                    packagePath = resolvePackagePath(module, packageName);
                    if( docFileExists(module, packagePath, fileName) ) {
                        printLink(out, namePart, moduleDocUrl, packagePath, fileName, anchor, cssClass, !hasText);
                        return;
                    }
                    // bah, it's not there
                }
            }
            printUnresolvableLink(out, content);
        }
    }

    private void printUnresolvableLink(StringBuilder out, String content) {
        out.append("[[").append(content).append("]]");
    }

    private void printLink(StringBuilder out, String namePart, String moduleDocUrl, String packagePath, String fileName, String anchor,
            String cssClass, boolean isCode) {
        out.append("<a href='");
        out.append(moduleDocUrl);
        if (!packagePath.isEmpty()) {
            out.append(packagePath);
            out.append(PATH_SEPARATOR);
        }
        out.append(fileName);
        if (anchor != null) {
            out.append("#");
            out.append(anchor);
        }
        out.append("'>");
        if(isCode){
            out.append("<code>");
        }
        if(cssClass != null){
            out.append("<span class='").append(cssClass).append("'>");
        }
        out.append(namePart);
        if(cssClass != null){
            out.append("</span>");
        }
        if(isCode){
            out.append("</code>");
        }
        out.append("</a>");
    }

    private String resolveNamePart(String content) {
        int pipeSeparatorIndex = content.indexOf(PIPE_SEPARATOR);
        if (pipeSeparatorIndex != -1) {
            return content.substring(0, pipeSeparatorIndex);
        } else {
            return content;
        }
    }

    private String resolveDeclPart(String content) {
        int pipeSeparatorIndex = content.indexOf(PIPE_SEPARATOR);
        if (pipeSeparatorIndex != -1) {
            return content.substring(pipeSeparatorIndex + 1, content.length());
        } else {
            return content;
        }
    }

    private String resolveDeclName(String declPart) {
        int packageSeparatorIndex = declPart.indexOf(PACKAGE_SEPARATOR);
        if (packageSeparatorIndex != -1) {
            return declPart.substring(packageSeparatorIndex + 2, declPart.length());
        } else {
            return declPart;
        }
    }

    private String resolvePackageName(String declPart) {
        int packageSeparatorIndex = declPart.indexOf(PACKAGE_SEPARATOR);
        if (packageSeparatorIndex != -1) {
            return declPart.substring(0, packageSeparatorIndex);
        } else {
            return currentModule.module.name; // default package is module root
        }
    }

    private String resolvePackagePath(ModuleVersion moduleVersion, String packageName) {
        String packagePath = packageName.substring(moduleVersion.module.name.length());
        if (packagePath.startsWith(DOT_SEPARATOR)) {
            packagePath = packagePath.substring(1);
        }
        return packagePath.replace(DOT_SEPARATOR, PATH_SEPARATOR);
    }

    private ModuleVersion resolveModule(String packageName) {
        if (currentModule.containsPackage(packageName)) {
            return currentModule;
        } else {
            ModuleVersion ret = resolveModuleInDependencies(currentModule, packageName);
            if(ret != null){
                return ret;
            }
            // perhaps it's from the language module implicit dependency?
            Module langModule = Module.findByName("ceylon.language");
            if(langModule != null){
                ModuleVersion langModuleVersion = langModule.getLastVersion();
                if(langModuleVersion != null && langModuleVersion.containsPackage(packageName))
                    return langModuleVersion;
            }
            // not found
            return null;
        }
    }        

    private ModuleVersion resolveModuleInDependencies(
            ModuleVersion module, String packageName) {
        for (Dependency dependency : module.dependencies) {
            ModuleVersion importedModule = dependency.moduleVersion;
            // FIXME: deal with JDK links
            // FIXME: deal with Maven links
            if(importedModule == null)
                continue;
            if (importedModule.containsPackage(packageName)) {
                return importedModule;
            }
            if(dependency.export){
                ModuleVersion ret = resolveModuleInDependencies(importedModule, packageName);
                if(ret != null)
                    return ret;
            }
        }
        return null;
    }

    private String resolveModuleDocUrl(ModuleVersion moduleVersion) {
        if( moduleVersion != null ) {
            StringBuilder path = new StringBuilder();
            path.append(moduleVersion.module.name.replace(DOT_SEPARATOR, PATH_SEPARATOR));
            path.append(PATH_SEPARATOR);
            path.append(moduleVersion.version);
            path.append(PATH_SEPARATOR);
            path.append(MODULE_DOC_API);
            path.append(PATH_SEPARATOR);

            try {
                return Util.viewRepoUrl(path.toString());
            } catch (NoRouteFoundException e) {
                // noop
            }
        }
        return null;
    }

    private String[] resolveFileNameAndAnchor(String declName) {
        String fileName = null;
        String anchor = null;
    
        String[] names = declName.split("\\" + DOT_SEPARATOR);
        String lastName = names[names.length - 1];
        String prevLastName = names.length > 1 ? names[names.length - 2] : null;
        String rest = names.length > 1 ? declName.substring(0, declName.lastIndexOf(DOT_SEPARATOR)) + "." : "";
    
        if (Character.isUpperCase(lastName.charAt(0))) {
            fileName = rest + lastName + ".type.html";
        }
        else if (prevLastName != null && Character.isUpperCase(prevLastName.charAt(0))) {
            fileName = rest + "type.html";
            anchor = lastName;
        }
        else if (prevLastName != null && Character.isLowerCase(prevLastName.charAt(0))) {
            fileName = rest + "object.html";
            anchor = lastName;
        }
        else {
            fileName = "index.html";
            anchor = lastName;
        }
    
        return new String[] { fileName, anchor };
    }

    private boolean docFileExists(ModuleVersion moduleVersion, String packagePath, String fileName) {
        File repoDir = Util.getRepoDir();
        File moduleDir = new File(repoDir, moduleVersion.module.name.replace(DOT_SEPARATOR, PATH_SEPARATOR) + PATH_SEPARATOR + moduleVersion.version + PATH_SEPARATOR + MODULE_DOC_API);
        File packageDir = new File(moduleDir, packagePath);
        File f = new File(packageDir, fileName);
        if (f.exists() && f.isFile()) {
            return true;
        }
        return false;
    }

}