package util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import models.MavenDependency;
import models.ModuleVersion;
import models.Upload;
import models.User;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import play.libs.XML;

public class ModuleChecker {
    
    public static class Member implements Comparable<Member> {
        public final CeylonElementType type;
        public final String name;
        public final String packageName;
        
        public Member(CeylonElementType type, String packageName, String name) {
            this.type = type;
            this.packageName = packageName;
            this.name = name;
        }

        @Override
        public int compareTo(Member other) {
            int pkg = packageName.compareTo(other.packageName);
            if(pkg != 0)
                return pkg;
            int typea = type.typeWeight();
            int typeb = other.type.typeWeight();
            if(typea != typeb)
                return typea < typeb ? -1 : 1;
            return name.compareTo(other.name);
        }
    }

    public static final Pattern CEYLON_MODULE_NAME_PATTERN = Pattern.compile("^(\\p{Ll}|_)(\\p{L}|\\p{Digit}|_)*(\\.(\\p{Ll}|_)(\\p{L}|\\p{Digit}|_)*)*$");

    public static List<Diagnostic> collectModulesAndDiagnostics(
            List<File> uploadedFiles, List<Module> modules, File uploadsDir, User user, Upload upload) {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        Map<String, File> fileByPath = new HashMap<String, File>();
        Set<String> alreadyTreatedArchives = new HashSet<String>(); 
        if (uploadedFiles.isEmpty() && modules.isEmpty()) {
            diagnostics.add(new Diagnostic("empty","Empty upload"));
        } else {
            for(File f : uploadedFiles){
                String name = f.getName();
                String path = getPathRelativeTo(uploadsDir, f);
                fileByPath.put(path, f);
                if(name.endsWith(".car")
                        || name.endsWith(".jar")
                        // don't even try to match js files if they are in module-doc or module-resources folders
                        || (name.endsWith(".js") && !name.endsWith("-model.js") && !path.contains("module-doc") && !path.contains("module-resources"))){
                    String pathBeforeDot = path.substring(0, path.lastIndexOf('.'));
                    // don't add a module for both the car, jar and js file
                    if (!alreadyTreatedArchives.add(pathBeforeDot)) {
                        continue;
                    }
                    int sep = name.indexOf('-');
                    if(sep == -1){
                        if (name.equals("default.car")
                                || name.equals("default.js")
                                || name.equals("default.jar")) {
                            diagnostics.add(new Diagnostic("error", "Default module not allowed."));
                        } else {
                            diagnostics.add(new Diagnostic("error", "Module artifact has no version: " + name));
                        }
                        continue;
                    }else if(name.lastIndexOf('-') != sep){
                        // we have more than one dash, it could be a jar with a dash in the module name and/or a dash in the version, let's
                        // try to guess the best separator location from the path
                        sep = guessBestDashSeparatorFromPath(sep, path);
                    }
                    int dot = name.lastIndexOf('.');
                    String module = name.substring(0, sep);
                    if(module.isEmpty()){
                        diagnostics.add(new Diagnostic("error", "Empty module name not allowed: "+name));
                        continue;
                    }
                    if(module.equals("default")){
                        diagnostics.add(new Diagnostic("error", "Default module not allowed: "+name));
                        continue;
                    }
                    String version = name.substring(sep+1, dot);
                    if(version.isEmpty()){
                        diagnostics.add(new Diagnostic("error", "Empty version number not allowed: "+name));
                        continue;
                    }
                    modules.add(new Module(module, version, path.substring(0, path.length()-name.length())));
                }
            }
            for(Module m : modules){
                checkModule(uploadsDir, fileByPath, m, user, modules, upload);
            }
            for(Module m : modules){
                checkModuleDependencyVersions(m);
            }
            if (modules.isEmpty()) {
                diagnostics.add(new Diagnostic("error", "No module defined"));
            }
        }
        if(!fileByPath.isEmpty()){
            for (String key : fileByPath.keySet()) {
                diagnostics.add(new Diagnostic("error", "Unknown file: " + key, key.substring(1)));
            }
        }

        return diagnostics;
    }

    private static int guessBestDashSeparatorFromPath(int sep, String path) {
        int lastPathSep = path.lastIndexOf(File.separatorChar);
        if(lastPathSep == -1)
            return sep;
        // path starts with a slash: /foo/bar-gee/2-beta/foo.bar-gee-2-beta.jar
        if(path.length() < 2 || lastPathSep < 2)
            return sep;
        // get rid of the initial slash and up to the last slash not included
        String firstPart = path.substring(1, lastPathSep);
        // should have foo/bar-gee/2-beta now, extract the name part
        if(firstPart.isEmpty())
            return sep;
        int startOfVersion = firstPart.lastIndexOf(File.separatorChar);
        if(startOfVersion == -1)
            return sep;
        // should be foo/bar-gee now
        String moduleNamePath = firstPart.substring(0, startOfVersion);
        // now convert it to module name: should be foo.bar-gee
        String moduleName = moduleNamePath.replace(File.separatorChar, '.');
        // now extract the last part: foo.bar-gee-2-beta.jar
        String lastPart = path.substring(lastPathSep+1);
        // check that the last part starts with moduleName + "-"
        if(lastPart.startsWith(moduleName+"-"))
            return moduleName.length(); // this should be pointing at the right dash
        // we failed our assumptions
        return sep;
    }

    private static void checkModuleDependencyVersions(Module m) {
        // the given module has to be a JVM car module
        for(Import dep : m.carDependencies){
            if(dep.existingDependency != null){
                // no need to skip JDK modules deps here since they can't already exist in Herd
                // only check cars for binary version
                if (m.car.exists && dep.existingDependency.isCarPresent
                        && (m.jvmBinMajor != dep.existingDependency.jvmBinMajor
                        || m.jvmBinMinor != dep.existingDependency.jvmBinMinor)) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on an incompatible Ceylon version: " + dep.name + "/" + dep.version));
                }
                if (!m.car.exists && !m.jar.exists) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on a non-JVM module: " + dep.name + "/" + dep.version));
                }
            }
            if(dep.newDependency != null){
                // only check cars for binary version
                if (m.car.exists && dep.newDependency.car.exists
                        && (m.jvmBinMajor != dep.newDependency.jvmBinMajor
                        || m.jvmBinMinor != dep.newDependency.jvmBinMinor)) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on an incompatible Ceylon version: " + dep.name + "/" + dep.version));
                }
                if (!m.car.exists && !m.jar.exists) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on a non-JVM module: " + dep.name + "/" + dep.version));
                }
            }
        }
        // the given module has to be a JS module
        for(Import dep : m.jsDependencies){
            if(dep.existingDependency != null){
                // no need to skip JDK modules deps here since they can't already exist in Herd
                // only check cars for binary version
                if (m.js.exists && dep.existingDependency.isJsPresent
                        && (m.jsBinMajor != dep.existingDependency.jsBinMajor
                        || m.jsBinMinor != dep.existingDependency.jsBinMinor)) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on an incompatible Ceylon version: " + dep.name + "/" + dep.version));
                }
                if (!m.js.exists) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on a non-JS module: " + dep.name + "/" + dep.version));
                }
            }
            if(dep.newDependency != null){
                // only check cars for binary version
                if (m.js.exists && dep.newDependency.js.exists
                        && (m.jsBinMajor != dep.newDependency.jsBinMajor
                        || m.jsBinMinor != dep.newDependency.jsBinMinor)) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on an incompatible Ceylon version: " + dep.name + "/" + dep.version));
                }
                if (!m.js.exists) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on a non-JS module: " + dep.name + "/" + dep.version));
                }
            }
        }
        if (m.car.exists && m.js.exists) {
            if (m.carDependencies.size() != m.jsDependencies.size()
                    || !m.carDependencies.containsAll(m.jsDependencies)
                    || !m.jsDependencies.containsAll(m.carDependencies)) {
                m.diagnostics.add(new Diagnostic("error", "The list of dependencies defined by the .car file and the .js file are NOT the same"));
                return;
            }
        }
    }

    public static void checkModule(File uploadsDir,
            Map<String, File> fileByPath, Module m, User user, List<Module> modules, Upload upload) {

        // check the path first (we always start and end with a separator)
        String expectedPath =
                File.separatorChar
                + m.name.replace('.', File.separatorChar)
                + File.separatorChar + m.version
                + File.separatorChar;
        if(!expectedPath.equals(m.path)){
            m.diagnostics.add(new Diagnostic("error", "Module is not in the right path: "+m.path+" (expecting "+expectedPath+")"));
        }

        models.Project project = models.Project.findOwner(m.name);
        if(project == null){
            // nobody owns it, but perhaps we already have a claim for it
            project = models.Project.findForOwner(m.name, user);
            m.diagnostics.add(new Diagnostic("error", "You do not own this module", project));
        }else{
            // do we own it?
            if (project.owner == user) {
                m.diagnostics.add(new Diagnostic("success", "You own this module"));
            } else {
                // we don't own it but we may be admin
                models.Module publishedModule = models.Module.findByName(m.name);
                if (publishedModule == null || !publishedModule.canEdit(user)) {
                    // we're not the owner, and not admin, but perhaps we already have a claim for it
                    project = models.Project.findForOwner(m.name, user);
                    m.diagnostics.add(new Diagnostic("error", "You do not own this module", project));
                } else {
                    m.diagnostics.add(new Diagnostic("success", "You are admin on this module"));
                }
            }
        }
        
        models.ModuleVersion publishedModule = models.ModuleVersion.findByVersion(m.name, m.version);
        if (publishedModule != null) {
            m.diagnostics.add(new Diagnostic("error", "Module already published"));
        }

        // jar check first

        String jarName = m.name + "-" + m.version + ".jar";
        String jarPath = m.path + jarName;
        m.jar.exists = fileByPath.containsKey(jarPath);
        if(m.jar.exists){
            fileByPath.remove(jarPath); // jar
            m.jar.checksum = handleChecksumFile(uploadsDir, fileByPath, m, jarName, "Jar", false);
        }

        String jarModulePropertiesName = "module.properties";
        String jarModulePropertiesPath = m.path + jarModulePropertiesName;
        boolean hasJarModulesProperties = fileByPath.containsKey(jarModulePropertiesPath);
        if(hasJarModulesProperties){
            fileByPath.remove(jarModulePropertiesPath);
            if(!m.jar.exists){
                m.diagnostics.add(new Diagnostic("error", "module properties file only supported with jar upload"));
            }
            loadJarModuleProperties(uploadsDir, jarModulePropertiesPath, m, modules, upload);
        }

        String jarModuleXmlName = "module.xml";
        String jarModuleXmlPath = m.path + jarModuleXmlName;
        boolean hasJarXmlProperties = fileByPath.containsKey(jarModuleXmlPath);
        if(hasJarXmlProperties){
            fileByPath.remove(jarModuleXmlPath);
            if(!m.jar.exists){
                m.diagnostics.add(new Diagnostic("error", "module xml file only supported with jar upload"));
            }
            loadJarModuleXml(uploadsDir, jarModuleXmlPath, m, modules, upload);
            if(hasJarModulesProperties){
                m.diagnostics.add(new Diagnostic("error", "only one of module xml or properties file supported"));
            }
        }

        if(m.jar.exists && !hasJarXmlProperties && !hasJarModulesProperties){
            Diagnostic diag = new Diagnostic("warning", "jar file with no module descriptor");
            diag.noModuleDescriptor = true;
            m.diagnostics.add(diag);
        }
        
        // car check

        String carName = m.name + "-" + m.version + ".car";
        if (checkArtifact("car", carName, uploadsDir, fileByPath, m, m.car, true)) {
            String artifactPath = m.path + carName;
            loadModuleInfoFromCar(uploadsDir, artifactPath, m, modules, upload);
            checkIsRunnable(uploadsDir, artifactPath, m);
            checkThatClassesBelongToModule(uploadsDir, artifactPath, m);
            loadClassNames(uploadsDir, artifactPath, m);
        }

        // js check

        String jsName = m.name + "-" + m.version + ".js";
        if (checkArtifact("js", jsName, uploadsDir, fileByPath, m, m.js, true)) {
            String jsPath = m.path + jsName;
            loadModuleInfoFromJs(uploadsDir, jsPath, m, modules, upload);
            String jsModelName = m.name + "-" + m.version + "-model.js";
            if (!checkArtifact("js model", jsModelName, uploadsDir, fileByPath, m, m.jsModel, false)) {
                if (m.jsBinMajor >= 7) {
                    m.diagnostics.add(new Diagnostic("error", "Missing Js Model", m.path + jsName));
                }
            }
        }

        // must have at least js or car or jar
        if (!m.car.exists && !m.js.exists && !m.jar.exists) {
            m.diagnostics.add(new Diagnostic("error", "Module must have at least a car, jar or js archive"));
        }

        // src check

        String srcName = m.name + "-" + m.version + ".src";
        checkArtifact("source", srcName, uploadsDir, fileByPath, m, m.source, true);

        // scripts check

        String scriptsName = m.name + "-" + m.version + ".scripts.zip";
        checkArtifact("scripts", scriptsName, uploadsDir, fileByPath, m, m.scripts, true);

        // doc check
        folderCheck("docs", "module-doc", "module-doc.zip", uploadsDir, fileByPath, m, m.docs, true);
        
        // resources check
        folderCheck("resources", "module-resources", "module-resources.zip", uploadsDir, fileByPath, m, m.resources, true);
        
        if (m.car.exists || m.js.exists) {
            checkCeylonModuleName(m);
        }
        
        // second jar check
        
        // if the jar is alone it's good. Otherwise an error was already added
        if (m.jar.exists && m.js.missing() && m.docs.missing() && m.car.missing()
                && m.source.missing() && m.scripts.missing() && m.resources.missing()) {
            m.diagnostics.add(new Diagnostic("success", "Has jar: " + jarName));
        }
    }

    private static boolean checkArtifact(String name, String artifactName, File uploadsDir, Map<String, File> fileByPath, Module m, Artifact art, boolean showWarning) {
        String artifactPath = m.path + artifactName;
        art.exists = fileByPath.containsKey(artifactPath);
        if(art.exists){
            fileByPath.remove(artifactPath);

            if (!m.jar.exists) {
                m.diagnostics.add(new Diagnostic("success", "Has " + name + ": " + artifactName));
            } else {
                m.diagnostics.add(new Diagnostic("error", "If a module contains a jar it cannot contain other archives"));
            }

            art.checksum = handleChecksumFile(uploadsDir, fileByPath, m, artifactName, name, m.jar.exists);
            return true;
        }else if (!m.jar.exists && showWarning) {
            m.diagnostics.add(new Diagnostic("warning", "Missing " + name + " archive"));
        }
        return false;
    }

    private static void folderCheck(String name, String folderName, String folderZipName, File uploadsDir, Map<String, File> fileByPath, Module m, ZippedFolderArtifact art, boolean showWarning) {
        // zipped folder check
        File folderZipFile = new File(uploadsDir, m.path + folderZipName);
        if (checkArtifact(name, folderZipName, uploadsDir, fileByPath, m, art, false)) {
            // All ok
        }else if (folderZipFile.exists() && !folderZipFile.isFile()) {
            m.diagnostics.add(new Diagnostic("error", folderZipName + " exists but is not a file"));
        }else if (!m.jar.exists && showWarning) {
            m.diagnostics.add(new Diagnostic("warning", "Missing " + name + " archive archive"));
        }

        // unzipped folder check
        String dirName = m.path + folderName;
        File folderDir = new File(uploadsDir, dirName);
        if(folderDir.isDirectory()){
            art.hasUnzipped = true;
            if (!m.jar.exists) {
                m.diagnostics.add(new Diagnostic("success", "Has " + name));
            } else {
                m.diagnostics.add(new Diagnostic("error", "If a module contains a jar it cannot contain other archives"));
            }
            String prefix = m.path + folderName + File.separator;
            Iterator<String> iterator = fileByPath.keySet().iterator();
            while(iterator.hasNext()){
                String key = iterator.next();
                // count all the doc files
                if (key.startsWith(prefix)) {
                    iterator.remove();
                }
            }
        }else if (folderDir.exists() && !folderDir.isDirectory()) {
            m.diagnostics.add(new Diagnostic("error", folderName + " exists but is not a directory"));
        }else if (!m.jar.exists && showWarning) {
            m.diagnostics.add(new Diagnostic("warning", "Missing " + name));
        }
    }

    private static ChecksumState handleChecksumFile(File uploadsDir, Map<String, File> fileByPath,
            Module m, String fileName, String fileType, boolean skipDiag) {
        String checksumPath = m.path + fileName + ".sha1";
        if (fileByPath.containsKey(checksumPath)) {
            fileByPath.remove(checksumPath); // checksum
            File jsFile = new File(uploadsDir, m.path + fileName);
            if (checkChecksum(uploadsDir, checksumPath, jsFile)) {
                if (!skipDiag) {
                    m.diagnostics.add(new Diagnostic("success", fileType + " checksum valid"));
                }
                return ChecksumState.valid;
            } else {
                m.diagnostics.add(checksumDiagnostic("error", "Invalid " + fileType + " checksum", m.path + fileName));
                return ChecksumState.invalid;
            }
        } else {
            if (!skipDiag) {
                m.diagnostics.add(checksumDiagnostic("error", "Missing " + fileType + " checksum", m.path + fileName));
            }
            return ChecksumState.missing;
        }
    }

    private static void checkCeylonModuleName(Module m) {
        if (!CEYLON_MODULE_NAME_PATTERN.matcher(m.name).matches()) {
            m.diagnostics.add(new Diagnostic("error", "Module name is not valid"));
        }
    }
    
    private static void checkThatClassesBelongToModule(File uploadsDir, String carPath, Module m) {
        // Hack: ceylon.language has special classes, and only we can publish it so we know what we're doing
        String errorLevel = "ceylon.language".equals(m.name) ? "warning" : "error";
        String modulePackagePath = m.name.replace('.', File.separatorChar) + File.separatorChar;
        try {
            ZipFile zipFile = new ZipFile(new File(uploadsDir, carPath));
            try {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    if (!entry.isDirectory()) {
                        String fileName = entry.getName();
                        if (fileName.endsWith(".class") && !fileName.startsWith(modulePackagePath)) {
                            m.diagnostics.add(new Diagnostic(errorLevel, "Class doesn't belong to module: " + fileName));
                        }
                    }
                }
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
            handleIOException(e);
        }
    }
    
    private static void loadClassNames(File uploadsDir, String carPath, Module m){
        try {
            ZipFile zipFile = new ZipFile(new File(uploadsDir, carPath));
            try {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    if (!entry.isDirectory()) {
                        String fileName = entry.getName();
                        if (fileName.endsWith(".class")) {
                            loadClassName(zipFile, entry, m);
                        }
                    }
                }
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
            handleIOException(e);
        }
    }
    
    private static void loadClassName(ZipFile zip, ZipEntry entry, Module m) throws IOException {
        DataInputStream inputStream = new DataInputStream(zip.getInputStream(entry));
        ClassFile classFile = new ClassFile(inputStream);
        inputStream.close();

        AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag);
        AnnotationsAttribute invisible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.invisibleTag);
        
        // ignore what we must ignore
        if(visible != null && visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Ignore") != null)
            return;

        String name = classFile.getName();
        int lastDot = name.lastIndexOf('.');
        String packageName = lastDot != -1 ? name.substring(0, lastDot) : ""; 
        String simpleName = lastDot != -1 ? name.substring(lastDot+1) : name; 

        boolean ceylon = visible != null && visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Ceylon") != null;
        boolean removeTrailingUnderscore = false;
        CeylonElementType type = null;
        if(ceylon){
            // ignore local types
            if(visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.LocalContainer") != null)
                return;
            // ignore module and package descriptors
            if(visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Module") != null
                    || visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Package") != null)
                return;
            if(visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Attribute") != null
                    || visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Object") != null
                    || visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Method") != null)
                removeTrailingUnderscore = true;
            if(visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Attribute") != null)
                type = CeylonElementType.Value;
            else if(visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Method") != null){
                if(invisible != null && invisible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.AnnotationInstantiation") != null)
                    type = CeylonElementType.AnnotationConstructor;
                else
                    type = CeylonElementType.Function;
            }else if(visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Object") != null)
                type = CeylonElementType.Object;
            else if(visible.getAnnotation("ceylon.language.AnnotationAnnotation$annotation$") != null)
                type = CeylonElementType.Annotation;
            // FIXME: temporarily filter generated annotations like this because I don't think we should see them
            if(simpleName.endsWith("$annotation$") || simpleName.endsWith("$annotations$"))
                return;
            // remove any leading dollar
            if(simpleName.startsWith("$"))
                simpleName = simpleName.substring(1);
            // ceylon names have mangling for interface members that we pull to toplevel
            simpleName = simpleName.replace("$impl$", ".");
            // remove trailing underscore if required
            if(removeTrailingUnderscore && simpleName.endsWith("_"))
                simpleName = simpleName.substring(0, simpleName.length()-1);
        }
        if(type == null){
            if((classFile.getAccessFlags() & ANNOTATION_BIT) != 0)
                type = CeylonElementType.Annotation;
            else if(classFile.isInterface())
                type = CeylonElementType.Interface;
            else
                type = CeylonElementType.Class;
        }
        // skip local types
        if(classFile.getAttribute("EnclosingMethod") != null)
            return;
        // turn any dollar sep into a dot
        simpleName = simpleName.replace('$', '.');
        // special fix for ceylon.language
        if(m.name.equals("ceylon.language") && !packageName.startsWith("ceylon.language"))
            return;
        m.addMember(type, packageName, simpleName);
    }

    private static final int ANNOTATION_BIT = 1 << 13;
    
    private static void loadJarModuleProperties(File uploadsDir, String fileName, Module m, List<Module> modules, Upload upload) {
        File f = new File(uploadsDir, fileName);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            try{
                String line;
                int nr = 0;
                while((line = reader.readLine()) != null){
                    line = line.trim();
                    // remove # comments
                    int hashPos = line.indexOf('#');
                    if(hashPos > -1)
                        line = line.substring(0, hashPos).trim();
                    nr++;
                    // skip empty lines
                    if(line.isEmpty())
                        continue;
                    // make sure line is valid
                    int equalsPos = line.indexOf('=');
                    if(equalsPos == -1){
                        m.diagnostics.add(new Diagnostic("error", "Invalid modules.properties line "+nr+": "+line));
                        continue;
                    }
                    String name = line.substring(0, equalsPos);
                    boolean shared = false;
                    boolean optional = false;
                    if(name.startsWith("+")){
                        shared = true;
                        name = name.substring(1);
                    }
                    if(name.endsWith("?")){
                        optional = true;
                        name = name.substring(0,name.length()-1);
                    }
                    String version = line.substring(equalsPos+1);
                    if(name.isEmpty()){
                        m.diagnostics.add(new Diagnostic("error", "Invalid modules.properties line "+nr+" (name is empty): "+line));
                        continue;
                    }
                    if(version.isEmpty()){
                        m.diagnostics.add(new Diagnostic("error", "Invalid modules.properties line "+nr+" (version is empty): "+line));
                        continue;
                    }
                    // must make sure it exists
                    checkDependencyExists(name, version, optional, shared, m, modules, upload, m.carDependencies);
                }
            }finally{
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            m.diagnostics.add(new Diagnostic("error", "Invalid modules file: "+e.getMessage()));
        }
    }

    private static void loadJarModuleXml(File uploadsDir, String fileName, Module m, List<Module> modules, Upload upload) {
        File f = new File(uploadsDir, fileName);
        try {
            Document document = XML.getDocument(f);
            Element root = document.getDocumentElement();
            if(!root.getTagName().equals("module")){
                m.diagnostics.add(new Diagnostic("error", "module.xml: Invalid module root element: "+root.getTagName()));
                return;
            }
            String moduleName = root.getAttribute("name");
            if(moduleName == null || moduleName.isEmpty()){
                m.diagnostics.add(new Diagnostic("error", "module.xml: Missing module name"));
            }
            if(!moduleName.equals(m.name)){
                m.diagnostics.add(new Diagnostic("error", "module.xml: Invalid module name: "+moduleName));
            }
            String moduleVersion = root.getAttribute("slot");
            if(moduleVersion == null || moduleVersion.isEmpty()){
                m.diagnostics.add(new Diagnostic("error", "module.xml: Missing module version"));
            }
            if(!moduleVersion.equals(m.version)){
                m.diagnostics.add(new Diagnostic("error", "module.xml: Invalid module version: "+moduleVersion));
            }
            NodeList dependenciesNodes = root.getElementsByTagName("dependencies");
            for(int i=0;i<dependenciesNodes.getLength();i++){
                Node node = dependenciesNodes.item(i);
                if(node instanceof Element == false){
                    m.diagnostics.add(new Diagnostic("error", "module.xml: Invalid dependencies node: "+node));
                    continue;
                }
                Element dependencies = (Element) node;
                NodeList dependencyNodes = dependencies.getElementsByTagName("module");
                for(int j=0;j < dependencyNodes.getLength();j++){
                    Node dependencyNode = dependencyNodes.item(j);
                    if(dependencyNode instanceof Element == false){
                        m.diagnostics.add(new Diagnostic("error", "module.xml: Invalid dependency node: "+dependencyNode));
                        continue;
                    }
                    Element dependency = (Element) dependencyNode;
                    String name = dependency.getAttribute("name");
                    if(name == null || name.isEmpty()){
                        m.diagnostics.add(new Diagnostic("error", "module.xml: Missing dependency name: "+dependency));
                        continue;
                    }
                    String version = dependency.getAttribute("slot");
                    if(version == null || version.isEmpty()){
                        m.diagnostics.add(new Diagnostic("error", "module.xml: Missing dependency version: "+dependency));
                        continue;
                    }
                    String optional = dependency.getAttribute("optional");
                    boolean isOptional = "true".equals(optional);

                    String export = dependency.getAttribute("export");
                    boolean isExported = "true".equals(export);
                    // must make sure it exists
                    checkDependencyExists(name, version, isOptional, isExported, m, modules, upload, m.carDependencies);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            m.diagnostics.add(new Diagnostic("error", "Invalid modules file: "+e.getMessage()));
        }
    }

    private static Diagnostic checksumDiagnostic(String type, String message, String file) {
        Diagnostic diagnostic = new Diagnostic(type, message);
        diagnostic.missingChecksum = true;
        diagnostic.fileToChecksum = file;
        return diagnostic;
    }

    private static void loadModuleInfoFromCar(File uploadsDir, String carName, Module m, List<Module> modules, Upload upload) {
        try {
            ZipFile car = new ZipFile(new File(uploadsDir, carName));

            try{
                // try first with 1.1 format
                ZipEntry moduleEntry = car.getEntry(m.name.replace('.', '/') + "/$module_.class");
                if(moduleEntry == null){
                    // try first with M4 format
                    moduleEntry = car.getEntry(m.name.replace('.', '/') + "/module_.class");
                    if(moduleEntry == null){
                        // try with pre-M4 format
                        moduleEntry = car.getEntry(m.name.replace('.', '/') + "/module.class");
                        
                        if(moduleEntry == null){
                            m.diagnostics.add(new Diagnostic("error", ".car file does not contain module information"));
                            return;
                        }
                    }
                }
                m.diagnostics.add(new Diagnostic("success", ".car file contains module descriptor"));

                DataInputStream inputStream = new DataInputStream(car.getInputStream(moduleEntry));
                ClassFile classFile = new ClassFile(inputStream);
                inputStream.close();

                AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag);
                
                // ceylon version info

                Annotation ceylonAnnotation = visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Ceylon");
                if(ceylonAnnotation == null){
                    m.diagnostics.add(new Diagnostic("error", ".car does not contain @Ceylon annotation on module.class"));
                    return;
                }
                m.diagnostics.add(new Diagnostic("success", ".car file module descriptor has @Ceylon annotation"));

                Integer major = getOptionalInt(ceylonAnnotation, "major", 0, m);
                Integer minor = getOptionalInt(ceylonAnnotation, "minor", 0, m);
                if (major == null || minor == null) {
                    m.diagnostics.add(new Diagnostic("warning", ".car file has no binary version"));
                    return;
                }
                m.jvmBinMajor = major;
                m.jvmBinMinor = minor;
                m.diagnostics.add(new Diagnostic("success", ".car file has binary version " + major + "." + minor));
                
                // module info
                
                Annotation moduleAnnotation = visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Module");
                if(moduleAnnotation == null){
                    m.diagnostics.add(new Diagnostic("error", ".car does not contain @Module annotation on module.class"));
                    return;
                }
                m.diagnostics.add(new Diagnostic("success", ".car file module descriptor has @Module annotation"));

                String name = getString(moduleAnnotation, "name", m, false);
                String version = getString(moduleAnnotation, "version", m, false);
                if (name == null || version == null) {
                    return;
                }
                if(!name.equals(m.name)){
                    m.diagnostics.add(new Diagnostic("error", ".car file contains unexpected module: "+name));
                    return;
                }
                if(!version.equals(m.version)){
                    m.diagnostics.add(new Diagnostic("error", ".car file contains unexpected module version: "+version));
                    return;
                }
                m.diagnostics.add(new Diagnostic("success", ".car file module descriptor has valid name/version"));

                // metadata
                m.license = getString(moduleAnnotation, "license", m, true);
                if (m.license != null) {
                    m.diagnostics.add(new Diagnostic("success", "License: " + m.license));
                }
                m.doc = getString(moduleAnnotation, "doc", m, true);
                if (m.doc != null) {
                    m.diagnostics.add(new Diagnostic("success", "Has doc string"));
                }
                m.authors = getStringArray(moduleAnnotation, "by", m, true);
                if (m.authors != null && m.authors.length != 0) {
                    m.diagnostics.add(new Diagnostic("success", "Has authors"));
                }
                
                // dependencies
                
                MemberValue dependencies = moduleAnnotation.getMemberValue("dependencies");
                if(dependencies == null){
                    m.diagnostics.add(new Diagnostic("success", ".car file has no dependencies"));
                    return; // we're good
                }
                if(!(dependencies instanceof ArrayMemberValue)){
                    m.diagnostics.add(new Diagnostic("error", "Invalid 'dependencies' annotation value (expecting array)"));
                    return;
                }
                MemberValue[] dependencyValues = ((ArrayMemberValue)dependencies).getValue();
                if(dependencyValues.length == 0){
                    m.diagnostics.add(new Diagnostic("success", ".car file has no dependencies"));
                    return; // we're good
                }
                for(MemberValue dependencyValue : dependencyValues){
                    checkCarDependency(dependencyValue, m, modules, upload);
                }
            }finally{
                car.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            m.diagnostics.add(new Diagnostic("error", "Invalid car file: "+e.getMessage()));
        }
    }

    private static void checkCarDependency(MemberValue dependencyValue, Module m, List<Module> modules, Upload upload) {
        if(!(dependencyValue instanceof AnnotationMemberValue)){
            m.diagnostics.add(new Diagnostic("error", "Invalid dependency value (expecting annotation)"));
            return;
        }
        Annotation dependency = ((AnnotationMemberValue)dependencyValue).getValue();
        if(!dependency.getTypeName().equals("com.redhat.ceylon.compiler.java.metadata.Import")){
            m.diagnostics.add(new Diagnostic("error", "Invalid 'dependency' value (expecting @Import)"));
            return;
        }
        String name = getString(dependency, "name", m, false);
        String version = getString(dependency, "version", m, false);
        if (name == null || version == null) {
            return;
        }
        if(name.isEmpty()){
            m.diagnostics.add(new Diagnostic("error", "Invalid empty dependency name"));
            return;
        }
        if(version.isEmpty()){
            m.diagnostics.add(new Diagnostic("error", "Invalid empty dependency version"));
            return;
        }

        MemberValue optionalValue = dependency.getMemberValue("optional");
        boolean optional = false;
        if(optionalValue != null){
            if(!(optionalValue instanceof BooleanMemberValue)){
                m.diagnostics.add(new Diagnostic("error", "Invalid @Import 'optional' value (expecting boolean)"));
                return;
            }
            optional = ((BooleanMemberValue)optionalValue).getValue();
        }

        MemberValue exportValue = dependency.getMemberValue("export");
        boolean export = false;
        if(exportValue != null){
            if(!(exportValue instanceof BooleanMemberValue)){
                m.diagnostics.add(new Diagnostic("error", "Invalid @Import 'export' value (expecting boolean)"));
                return;
            }
            export = ((BooleanMemberValue)exportValue).getValue();
        }
        
        // must make sure it exists
        checkDependencyExists(name, version, optional, export, m, modules, upload, m.carDependencies);
    }

    private static void checkDependencyExists(String name, String version, boolean optional, boolean export,
            Module m, List<Module> modules, Upload upload, List<Import> dependencies) {
        String lead;
        if(optional && export)
            lead = "Shared and optional dependency";
        else if(optional)
            lead = "Optional dependency";
        else if(export)
            lead = "Shared dependency";
        else
            lead = "Dependency";
        lead = lead + " "+name+"/"+version;
        // JDK modules are always available
        if(JDKUtil.isJdkModule(name)){
            m.diagnostics.add(new Diagnostic("success", lead+" is a JDK module"));
            dependencies.add(new Import(name, version, optional, export));
            return;
        }
        // try to find it in the list of uploaded modules
        for(Module module : modules){
            if(module.name.equals(name) && module.version.equals(version)){
                m.diagnostics.add(new Diagnostic("success", lead+" is to be uploaded"));
                dependencies.add(new Import(name, version, optional, export, module));
                if(upload != null && upload.findMavenDependency(name, version) != null){
                    Diagnostic diagnostic = new Diagnostic("warning", lead+" resolved from Maven Central but present in your upload");
                    diagnostic.dependencyResolvedFromMaven = true;
                    diagnostic.dependencyName = name;
                    diagnostic.dependencyVersion = version;
                    m.diagnostics.add(diagnostic);
                }
                return;
            }
        }
        // try to find it in the repo
        models.ModuleVersion dep = models.ModuleVersion.findByVersion(name, version);
        if(dep == null){
            if(optional){
                m.diagnostics.add(new Diagnostic("warning", lead+" was not found but is optional"));
                dependencies.add(new Import(name, version, optional, export));
            }else if(upload == null || upload.findMavenDependency(name, version) == null){
                Diagnostic diagnostic = new Diagnostic("error", lead+" cannot be found in upload or repo and is not optional");
                diagnostic.dependencyNotFound = upload != null; // This shows/hides the "Resolve from Maven" button
                diagnostic.dependencyName = name;
                diagnostic.dependencyVersion = version;
                m.diagnostics.add(diagnostic);
            }else{
                dependencies.add(new Import(name, version, optional, export, upload.findMavenDependency(name, version)));
                Diagnostic diagnostic = new Diagnostic("success", lead+" resolved from Maven Central");
                diagnostic.dependencyResolvedFromMaven = true;
                diagnostic.dependencyName = name;
                diagnostic.dependencyVersion = version;
                m.diagnostics.add(diagnostic);
            }
        }else{
            dependencies.add(new Import(name, version, optional, export, dep));
            m.diagnostics.add(new Diagnostic("success", lead+" present in repo"));
            if(upload != null && upload.findMavenDependency(name, version) != null){
                Diagnostic diagnostic = new Diagnostic("warning", lead+" resolved from Maven Central but present in Herd");
                diagnostic.dependencyResolvedFromMaven = true;
                diagnostic.dependencyName = name;
                diagnostic.dependencyVersion = version;
                m.diagnostics.add(diagnostic);
            }
        }
    }

    private static void checkIsRunnable(File uploadsDir, String carName, Module m) {
        // FIXME: do this in one go with the module loading
        try {
            ZipFile car = new ZipFile(new File(uploadsDir, carName));

            try{
                String name = m.jvmBinMajor >= 3 ? "run_" : "run";
                ZipEntry moduleEntry = car.getEntry(m.name.replace('.', '/') + "/" + name + ".class");
                if(moduleEntry == null){
                    return;
                }
                DataInputStream inputStream = new DataInputStream(car.getInputStream(moduleEntry));
                ClassFile classFile = new ClassFile(inputStream);
                inputStream.close();

                AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag);

                m.isRunnable=false;
                Annotation methodAnnotation = visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Method");
                if(methodAnnotation != null) {
                    MethodInfo runMethodInfo = (MethodInfo) classFile.getMethod("run");
                    MethodInfo mainMethodInfo = (MethodInfo) classFile.getMethod("main");
                    if(runMethodInfo != null && mainMethodInfo != null && mainMethodInfo.toString().endsWith("V")) {
                        m.isRunnable = AccessFlag.isPublic(mainMethodInfo.getAccessFlags());
                    }
                }
            } finally {
                car.close();
            }
        } catch (IOException e) {
            handleIOException(e);
        }
        if(m.isRunnable) {
            m.diagnostics.add(new Diagnostic("success", "Module is runnable"));
        }
    }

    private static String getString(Annotation annotation,
            String field, Module m, boolean missingOK) {
        MemberValue value = annotation.getMemberValue(field);
        if(value == null){
            if (!missingOK) {
                m.diagnostics.add(new Diagnostic("error", "Missing '" + field + "' annotation value"));
            }
            return null;
        }
        if(!(value instanceof StringMemberValue)){
            m.diagnostics.add(new Diagnostic("error", "Invalid '"+field+"' annotation value (expecting String)"));
            return null;
        }
        return ((StringMemberValue)value).getValue();
    }

    private static String[] getStringArray(Annotation annotation,
            String field, Module m, boolean missingOK) {
        MemberValue value = annotation.getMemberValue(field);
        if(value == null){
            if (!missingOK) {
                m.diagnostics.add(new Diagnostic("error", "Missing '" + field + "' annotation value"));
            }
            return null;
        }
        if(!(value instanceof ArrayMemberValue)){
            m.diagnostics.add(new Diagnostic("error", "Invalid '"+field+"' annotation value (expecting String[])"));
            return null;
        }
        MemberValue[] arrayValue = ((ArrayMemberValue)value).getValue();
        if(arrayValue == null){
            if (!missingOK) {
                m.diagnostics.add(new Diagnostic("error", "Missing '" + field + "' annotation value"));
            }
            return null;
        }
        String[] ret = new String[arrayValue.length];
        int i=0;
        for(MemberValue val : arrayValue){
            if(!(val instanceof StringMemberValue)){
                m.diagnostics.add(new Diagnostic("error", "Invalid '"+field+"' annotation value (expecting String[])"));
                return null;
            }
            ret[i++] = ((StringMemberValue)val).getValue();
        }
        return ret;
    }

    private static Integer getOptionalInt(Annotation annotation, String field, int defaultValue,
            Module m) {
        MemberValue value = annotation.getMemberValue(field);
        if(value == null){
            return defaultValue;
        }
        if(!(value instanceof IntegerMemberValue)){
            m.diagnostics.add(new Diagnostic("error", "Invalid '"+field+"' annotation value (expecting int)"));
            return null;
        }
        return ((IntegerMemberValue)value).getValue();
    }

    private static void loadModuleInfoFromJs(File uploadsDir, String jsName, Module m, List<Module> modules, Upload upload) {
        JsonObject model = loadJsonModel(new File(uploadsDir, jsName));
        if (model == null) {
            m.diagnostics.add(new Diagnostic("error", ".js file does not contain module information"));
            return;
        }
        m.diagnostics.add(new Diagnostic("success", ".js file contains module descriptor"));

        String name = asString(model.get("$mod-name"));
        String version = asString(model.get("$mod-version"));
        if(name == null || !name.equals(m.name)){
            m.diagnostics.add(new Diagnostic("error", ".js file contains unexpected module: "+name));
            return;
        }
        if(version == null || !version.equals(m.version)){
            m.diagnostics.add(new Diagnostic("error", ".js file contains unexpected module version: "+version));
            return;
        }
        m.diagnostics.add(new Diagnostic("success", ".js file module descriptor has valid name/version"));
        
        Integer major = null, minor = null;
        String bin = asString(model.get("$mod-bin"));
        if (bin != null) {
            int p = bin.indexOf('.');
            if (p >= 0) {
                major = Integer.parseInt(bin.substring(0, p));
                minor = Integer.parseInt(bin.substring(p + 1));
            } else {
                major = Integer.parseInt(bin);
            }
        }
        if (major == null || minor == null) {
            m.diagnostics.add(new Diagnostic("warning", ".js file has no binary version"));
        } else {
            m.jsBinMajor = major;
            m.jsBinMinor = minor;
            m.diagnostics.add(new Diagnostic("success", ".js file has binary version " + major + "." + minor));
        }

        JsonElement array = model.get("$mod-deps");
        if (array == null || (array.isJsonArray() && array.getAsJsonArray().size() == 0)){
            m.diagnostics.add(new Diagnostic("success", ".js file has no dependencies"));
            return; // we're good
        }
        if (array.isJsonArray()) {
            JsonArray deps = array.getAsJsonArray();
            for (JsonElement dep : deps) {
                checkJsDependency(dep, m, modules, upload);
            }
        } else {
            m.diagnostics.add(new Diagnostic("error", ".js meta model has unexpected structure"));
            return;
        }
    }

    private static void checkJsDependency(JsonElement dep, Module m, List<Module> modules, Upload upload) {
        String module = null;
        boolean optional = false;
        boolean exported = false;
        
        if (dep.isJsonPrimitive()) {
            module = asString(dep);
        } else if (dep.isJsonObject()) {
            JsonObject depObj = dep.getAsJsonObject();
            module = asString(depObj.get("path"));
            optional = depObj.has("opt");
            exported = depObj.has("exp");
        }
        if (module == null) {
            m.diagnostics.add(new Diagnostic("error", ".js meta model has unexpected structure"));
            return;
        }
        
        int p = module.indexOf('/');
        if (p == -1) {
            m.diagnostics.add(new Diagnostic("error", "Invalid dependency name " + module));
            return;
        }
        String name = module.substring(0, p);
        String version = module.substring(p + 1);
        if(name.isEmpty()){
            m.diagnostics.add(new Diagnostic("error", "Invalid empty dependency name"));
            return;
        }
        if(version.isEmpty()){
            m.diagnostics.add(new Diagnostic("error", "Invalid empty dependency version"));
            return;
        }

        if ("ceylon.language".equals(name)) {
            return;
        }
        
        // must make sure it exists
        checkDependencyExists(name, version, optional, exported, m, modules, null, m.jsDependencies);
    }

    private static String asString(JsonElement obj) {
        if (obj == null) {
            return null;
        } else {
            return obj.getAsString();
        }
    }

    private static JsonObject loadJsonModel(File jsFile) {
        try {
            // If what we have is a plain .js file (not a -model.js file)
            // we first check if a model file exists and if so we use that
            // one instead of the given file
            String name = jsFile.getName().toLowerCase();
            if (!name.endsWith("-model.js") && name.endsWith(".js")) {
                name = jsFile.getName();
                name = name.substring(0, name.length() - 3) + "-model.js";
                File modelFile = new File(jsFile.getParentFile(), name);
                if (modelFile.isFile()) {
                    jsFile = modelFile;
                }
            }
            JsonObject model = readJsonModel(jsFile);
            return model;
        } catch (IOException e) {
            return null;
        }
    }
    
    /** Find the metamodel declaration in a js file, parse it as a Map and return it. 
     * @throws IOException */
    public static JsonObject readJsonModel(File jsFile) throws IOException {
        // IMPORTANT
        // This method NEEDS to be able to return the meta model of any previous file formats!!!
        // It MUST stay backward compatible
        try (BufferedReader reader = new BufferedReader(new FileReader(jsFile))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if ((line.startsWith("ex$.$CCMM$=")
                        || line.startsWith("var $CCMM$=")
                        || line.startsWith("var $$METAMODEL$$=")
                        || line.startsWith("var $$metamodel$$=")) && line.endsWith("};")) {
                    line = line.substring(line.indexOf("{"), line.length()-1);
                    JsonParser jsonParser = new JsonParser();
                    JsonObject rv = (JsonObject)jsonParser.parse(line);
                    return rv;
                }
            }
            return null;
        }
    }

    private static boolean checkChecksum(File uploadsDir, String checksumPath, File checkedFile) {
        File checksumFile = new File(uploadsDir, checksumPath);
        try{
            String checksum = FileUtils.readFileToString(checksumFile);
            String realChecksum = sha1(checkedFile);
            return realChecksum.equals(checksum);
        }catch(Exception x){
            return false;
        }
    }

    public static String sha1(File file) throws IOException{
        InputStream is = new FileInputStream(file);
        try{
            String realChecksum = DigestUtils.shaHex(is);
            return realChecksum;
        }finally{
            is.close();
        }
    }

    private static String getPathRelativeTo(File uploadsDir, File f) {
        try{
            String prefix = uploadsDir.getCanonicalPath();
            String path = f.getCanonicalPath();
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
        }catch(IOException x){
            throw new RuntimeException(x);
        }
        throw new RuntimeException("Invalid path: "+f.getPath());
    }

    private static void handleIOException(IOException e) {
        // FIXME
        e.printStackTrace();
    }

    public static class Diagnostic {
        public String type;
        public String message;
        public String unknownPath;
        public models.Project project;
        public boolean projectClaim;
        public boolean missingChecksum;
        public String fileToChecksum;
        public boolean dependencyNotFound;
        public boolean dependencyResolvedFromMaven;
        public String dependencyName;
        public String dependencyVersion;
        public boolean noModuleDescriptor;

        Diagnostic(String type, String message){
            this.type = type;
            this.message = message;
        }

        public Diagnostic(String type, String message, String unknownPath) {
            this(type, message);
            this.unknownPath = unknownPath;
        }

        public Diagnostic(String type, String message, models.Project project) {
            this(type, message);
            this.project = project;
            this.projectClaim = true;
        }
    }

    public static class Import {
        public String name;
        public String version;
        public boolean export;
        public boolean optional;
        public ModuleVersion existingDependency;
        public Module newDependency;
        public MavenDependency mavenDependency;

        Import(String name, String version, boolean optional, boolean export, ModuleVersion dep){
            this(name, version, optional, export);
            this.existingDependency = dep;
        }

        Import(String name, String version, boolean optional, boolean export, MavenDependency dep){
            this(name, version, optional, export);
            this.mavenDependency = dep;
        }

        Import(String name, String version, boolean optional, boolean export, Module dep){
            this(name, version, optional, export);
            this.newDependency = dep;
        }

        Import(String name, String version, boolean optional, boolean export) {
            this.name = name;
            this.version = version;
            this.optional = optional;
            this.export = export;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((version == null) ? 0 : version.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Import other = (Import) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (version == null) {
                if (other.version != null)
                    return false;
            } else if (!version.equals(other.version))
                return false;
            return true;
        }
        
    }

    public enum ChecksumState {
        missing, invalid, valid
    }
    
    public static class Artifact {
        public boolean exists;
        public ChecksumState checksum = ChecksumState.missing;
        
        public boolean missing() {
            return !exists && checksum == ChecksumState.missing;
        }
    }
    
    public static class ZippedFolderArtifact extends Artifact {
        public boolean hasUnzipped;
        
        public boolean missing() {
            return super.missing() && !hasUnzipped;
        }
    }
    
    public static class Module {
        public String[] authors;
        public String doc;
        public String license;
        public List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        public String name;
        public String version;
        public String path;
        public Artifact jar = new Artifact();
        public Artifact car = new Artifact();
        public Artifact js = new Artifact();
        public Artifact jsModel = new Artifact();
        public Artifact source = new Artifact();
        public Artifact scripts = new Artifact();
        public ZippedFolderArtifact docs = new ZippedFolderArtifact();
        public ZippedFolderArtifact resources = new ZippedFolderArtifact();
        public int jvmBinMajor;
        public int jvmBinMinor;
        public int jsBinMajor;
        public int jsBinMinor;
        public List<Import> carDependencies = new LinkedList<Import>();
        public List<Import> jsDependencies = new LinkedList<Import>();
        public boolean isRunnable;
        public SortedSet<Member> members = new TreeSet<Member>();

        Module(String name, String version, String path){
            this.name = name;
            this.version = version;
            this.path = path;
        }

        public void addMember(CeylonElementType type, String packageName, String className) {
            members.add(new Member(type, packageName, className));
        }

        public String getType(){
            String worse = "success";
            for(Diagnostic d : diagnostics){
                if (d.type.equals("error")) {
                    return d.type;
                }
                if (d.type.equals("warning")) {
                    worse = d.type;
                }
            }
            return worse;
        }

        public String getDocPath(){
            return path.substring(1) + "module-doc" + "/" + "api" + "/" + "index.html";
        }
    }

    public static class UploadInfo {

        public List<Diagnostic> diagnostics;
        public List<Module> modules;
        public models.Upload upload;
        public String status;

        public UploadInfo(models.Upload upload, List<Module> modules,
                List<Diagnostic> diagnostics) {
            this.upload = upload;
            this.modules = modules;
            this.diagnostics = diagnostics;
            setStatus();
        }

        private void setStatus(){
            status = "success";
            for(Diagnostic d : diagnostics){
                if(d.type.equals("error")){
                    status = d.type;
                    return;
                }
                if(d.type.equals("warning")) {
                    status = d.type;
                    return;
                }
                if (d.type.equals("empty")) {
                    status = d.type;
                }

            }
            for(Module m : modules){
                String type = m.getType();
                if(type.equals("error")){
                    status = type;
                    return;
                }
                if (type.equals("warning")) {
                    status = type;
                }
            }
        }

        public boolean isPublishable(){
            return status.equals("success") || status.equals("warning"); 
        }
    }
}
