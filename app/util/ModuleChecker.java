package util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import models.ModuleVersion;
import models.User;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import play.libs.XML;

public class ModuleChecker {

    public static List<Diagnostic> collectModulesAndDiagnostics(
            List<File> uploadedFiles, List<Module> modules, File uploadsDir, User user) {
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
                        // don't even try to match js files if they are in module-doc folders
                        || (name.endsWith(".js") && !path.contains("module-doc"))){
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
                checkModule(uploadsDir, fileByPath, m, user, modules);
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

    private static void checkModuleDependencyVersions(Module m) {
        // the given module has to be a JVM car module
        for(Import dep : m.dependencies){
            if(dep.existingDependency != null){
                // no need to skip JDK modules deps here since they can't already exist in Herd
                // only check cars for binary version
                if (dep.existingDependency.isCarPresent
                        && (m.ceylonMajor != dep.existingDependency.ceylonMajor
                        || m.ceylonMinor != dep.existingDependency.ceylonMinor)) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on an incompatible Ceylon version: " + dep.name + "/" + dep.version));
                }
                if (!m.hasCar && !m.hasJar) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on a non-JVM module: " + dep.name + "/" + dep.version));
                }
            }
            if(dep.newDependency != null){
                // only check cars for binary version
                if (dep.newDependency.hasCar
                        && (m.ceylonMajor != dep.newDependency.ceylonMajor
                        || m.ceylonMinor != dep.newDependency.ceylonMinor)) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on an incompatible Ceylon version: " + dep.name + "/" + dep.version));
                }
                if (!m.hasCar && !m.hasJar) {
                    m.diagnostics.add(new Diagnostic("error", "Module depends on a non-JVM module: " + dep.name + "/" + dep.version));
                }
            }
        }
    }

    public static void checkModule(File uploadsDir,
            Map<String, File> fileByPath, Module m, User user, List<Module> modules) {

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
        m.hasJar = fileByPath.containsKey(jarPath);
        if(m.hasJar){
            fileByPath.remove(jarPath); // jar
            String checksumPath = m.path + jarName + ".sha1";
            m.hasJarChecksum = fileByPath.containsKey(checksumPath);
            if (m.hasJarChecksum) {
                fileByPath.remove(checksumPath); // jar checksum
                File jarFile = new File(uploadsDir, jarPath);
                m.jarChecksumValid = checkChecksum(uploadsDir, checksumPath, jarFile);
                if (m.jarChecksumValid) {
                    m.diagnostics.add(new Diagnostic("success", "Jar checksum valid"));
                } else {
                    m.diagnostics.add(checksumDiagnostic("error", "Invalid Jar checksum", m.path + jarName));
                }
            } else {
                m.diagnostics.add(checksumDiagnostic("error", "Missing Jar checksum", m.path + jarName));
            }
        }

        String jarModulePropertiesName = "module.properties";
        String jarModulePropertiesPath = m.path + jarModulePropertiesName;
        boolean hasJarModulesProperties = fileByPath.containsKey(jarModulePropertiesPath);
        if(hasJarModulesProperties){
            fileByPath.remove(jarModulePropertiesPath);
            if(!m.hasJar){
                m.diagnostics.add(new Diagnostic("error", "module properties file only supported with jar upload"));
            }
            loadJarModuleProperties(uploadsDir, jarModulePropertiesPath, m, modules);
        }

        String jarModuleXmlName = "module.xml";
        String jarModuleXmlPath = m.path + jarModuleXmlName;
        boolean hasJarXmlProperties = fileByPath.containsKey(jarModuleXmlPath);
        if(hasJarXmlProperties){
            fileByPath.remove(jarModuleXmlPath);
            if(!m.hasJar){
                m.diagnostics.add(new Diagnostic("error", "module xml file only supported with jar upload"));
            }
            loadJarModuleXml(uploadsDir, jarModuleXmlPath, m, modules);
            if(hasJarModulesProperties){
                m.diagnostics.add(new Diagnostic("error", "only one of module xml or properties file supported"));
            }
        }

        // car check

        String carName = m.name + "-" + m.version + ".car";
        String carPath = m.path + carName;
        m.hasCar = fileByPath.containsKey(carPath);
        if(m.hasCar){
            fileByPath.remove(carPath); // car

            if (!m.hasJar) {
                m.diagnostics.add(new Diagnostic("success", "Has car: " + carName));
            } else {
                m.diagnostics.add(new Diagnostic("error", "If a module contains a jar it cannot contain other archives"));
            }

            String checksumPath = m.path + carName + ".sha1";
            m.hasChecksum = fileByPath.containsKey(checksumPath);
            if(m.hasChecksum){
                fileByPath.remove(checksumPath); // car checksum
                File carFile = new File(uploadsDir, carPath);
                m.checksumValid = checkChecksum(uploadsDir, checksumPath, carFile);
                if (m.checksumValid) {
                    if (!m.hasJar) {
                        m.diagnostics.add(new Diagnostic("success", "Checksum valid"));
                    }
                } else {
                    m.diagnostics.add(checksumDiagnostic("error", "Invalid checksum", m.path + carName));
                }
            }else if (!m.hasJar) {
                m.diagnostics.add(checksumDiagnostic("error", "Missing checksum", m.path + carName));
            }

            loadModuleInfo(uploadsDir, m.path+carName, m, modules);
            checkIsRunnable(uploadsDir,m.path+carName,m, modules);
        }else if (!m.hasJar) {
            m.diagnostics.add(new Diagnostic("warning", "Missing car archive"));
        }

        // js check

        String jsName = m.name + "-" + m.version + ".js";
        String jsPath = m.path + jsName;
        m.hasJs = fileByPath.containsKey(jsPath);
        if(m.hasJs){
            fileByPath.remove(jsPath); // js
            if (!m.hasJar) {
                m.diagnostics.add(new Diagnostic("success", "Has js: " + jsName));
            } else {
                m.diagnostics.add(new Diagnostic("error", "If a module contains a jar it cannot contain other archives"));
            }
            String checksumPath = m.path + jsName + ".sha1";
            m.hasJsChecksum = fileByPath.containsKey(checksumPath);
            if (m.hasJsChecksum) {
                fileByPath.remove(checksumPath); // js checksum
                File jsFile = new File(uploadsDir, jsPath);
                m.jsChecksumValid = checkChecksum(uploadsDir, checksumPath, jsFile);
                if (m.jsChecksumValid) {
                    m.diagnostics.add(new Diagnostic("success", "Js checksum valid"));
                } else {
                    m.diagnostics.add(checksumDiagnostic("error", "Invalid Js checksum", m.path + jsName));
                }
            } else {
                m.diagnostics.add(checksumDiagnostic("error", "Missing Js checksum", m.path + jsName));
            }
        }else if (!m.hasJar) {
            m.diagnostics.add(new Diagnostic("warning", "Missing js archive"));
        }

        // must have at least js or car or jar
        if (!m.hasCar && !m.hasJs && !m.hasJar) {
            m.diagnostics.add(new Diagnostic("error", "Module must have at least a car, jar or js archive"));
        }

        // src check

        String srcName = m.name + "-" + m.version + ".src";
        File srcFile = new File(uploadsDir, m.path + srcName);
        if(srcFile.exists()){
            m.hasSource = true;
            if (!m.hasJar) {
                m.diagnostics.add(new Diagnostic("success", "Has source"));
            } else {
                m.diagnostics.add(new Diagnostic("error", "If a module contains a jar it cannot contain other archives"));
            }
            fileByPath.remove(m.path + srcName); // source archive
            String srcChecksumPath = m.path + srcName + ".sha1";
            m.hasSourceChecksum = fileByPath.containsKey(srcChecksumPath);
            if(m.hasSourceChecksum){
                fileByPath.remove(srcChecksumPath); // car checksum
                m.sourceChecksumValid = checkChecksum(uploadsDir, srcChecksumPath, srcFile);
                if (m.sourceChecksumValid) {
                    if (!m.hasJar) {
                        m.diagnostics.add(new Diagnostic("success", "Source checksum valid"));
                    }
                } else {
                    m.diagnostics.add(checksumDiagnostic("error", "Invalid source checksum", m.path + srcName));
                }
            }else if (!m.hasJar) {
                m.diagnostics.add(checksumDiagnostic("error", "Missing source checksum", m.path + srcName));
            }
        }else if (!m.hasJar) {
            m.diagnostics.add(new Diagnostic("warning", "Missing source archive"));
        }

        // doc check

        String docName = m.path + "module-doc" + File.separator + "index.html";
        File docFile = new File(uploadsDir, docName);
        if(docFile.exists()){
            m.hasDocs = true;
            if (!m.hasJar) {
                m.diagnostics.add(new Diagnostic("success", "Has docs"));
            } else {
                m.diagnostics.add(new Diagnostic("error", "If a module contains a jar it cannot contain other archives"));
            }
            String prefix = m.path + "module-doc" + File.separator;
            Iterator<String> iterator = fileByPath.keySet().iterator();
            while(iterator.hasNext()){
                String key = iterator.next();
                // count all the doc files
                if (key.startsWith(prefix)) {
                    iterator.remove();
                }
            }
        }else if (!m.hasJar) {
            m.diagnostics.add(new Diagnostic("warning", "Missing docs"));
        }
        
        // second jar check
        
        // if the jar is alone it's good. Otherwise an error was already added
        if (m.hasJar && !m.hasJs && !m.hasCar && !m.hasChecksum && !m.hasDocs && !m.hasSource && !m.hasSourceChecksum) {
            m.diagnostics.add(new Diagnostic("success", "Has jar: " + jarName));
        }
    }

    private static void loadJarModuleProperties(File uploadsDir, String fileName, Module m, List<Module> modules) {
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
                    checkDependencyExists(name, version, m, modules);
                }
            }finally{
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            m.diagnostics.add(new Diagnostic("error", "Invalid modules file: "+e.getMessage()));
        }
    }

    private static void loadJarModuleXml(File uploadsDir, String fileName, Module m, List<Module> modules) {
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
                    if(isOptional){
                        m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" is optional"));
                        return;
                    }
                    // must make sure it exists
                    checkDependencyExists(name, version, m, modules);
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

    private static void loadModuleInfo(File uploadsDir, String carName, Module m, List<Module> modules) {
        try {
            ZipFile car = new ZipFile(new File(uploadsDir, carName));

            try{
                // try first with M4 format
                ZipEntry moduleEntry = car.getEntry(m.name.replace('.', '/') + "/module_.class");
                if(moduleEntry == null){
                    // try with pre-M4 format
                    moduleEntry = car.getEntry(m.name.replace('.', '/') + "/module.class");
                    
                    if(moduleEntry == null){
                        m.diagnostics.add(new Diagnostic("error", ".car file does not contain module information"));
                        return;
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
                    return;
                }
                m.ceylonMajor = major;
                m.ceylonMinor = minor;
                
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
                    m.diagnostics.add(new Diagnostic("success", "Module has no dependencies"));
                    return; // we're good
                }
                if(!(dependencies instanceof ArrayMemberValue)){
                    m.diagnostics.add(new Diagnostic("error", "Invalid 'dependencies' annotation value (expecting array)"));
                    return;
                }
                MemberValue[] dependencyValues = ((ArrayMemberValue)dependencies).getValue();
                if(dependencyValues.length == 0){
                    m.diagnostics.add(new Diagnostic("success", "Module has no dependencies"));
                    return; // we're good
                }
                for(MemberValue dependencyValue : dependencyValues){
                    checkDependency(dependencyValue, m, modules);
                }
            }finally{
                car.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            m.diagnostics.add(new Diagnostic("error", "Invalid car file: "+e.getMessage()));
        }
    }

    private static void checkDependency(MemberValue dependencyValue, Module m, List<Module> modules) {
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
        if(optionalValue != null){
            if(!(optionalValue instanceof BooleanMemberValue)){
                m.diagnostics.add(new Diagnostic("error", "Invalid @Import 'optional' value (expecting boolean)"));
                return;
            }
            boolean optional = ((BooleanMemberValue)optionalValue).getValue();
            if(optional){
                m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" is optional"));
                return;
            }
        }
        // must make sure it exists
        checkDependencyExists(name, version, m, modules);
    }

    private static void checkDependencyExists(String name, String version,
            Module m, List<Module> modules) {
        // skip JDK modules
        if(JDKUtil.isJdkModule(name))
            return;
        // try to find it in the list of uploaded modules
        for(Module module : modules){
            if(module.name.equals(name) && module.version.equals(version)){
                m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" is to be uploaded"));
                m.addDependency(name, version, module);
                return;
            }
        }
        // try to find it in the repo
        models.ModuleVersion dep = models.ModuleVersion.findByVersion(name, version);
        if(dep == null){
            m.diagnostics.add(new Diagnostic("error", "Dependency "+name+"/"+version+" cannot be found in upload or repo"));
        }else{
            m.addDependency(name, version, dep);
            m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" present in repo"));
        }
    }

    private static void checkIsRunnable(File uploadsDir, String carName, Module m, List<Module> modules) {
        // FIXME: do this in one go with the module loading
        try {
            ZipFile car = new ZipFile(new File(uploadsDir, carName));

            try{
                String name = m.ceylonMajor >= 3 ? "run_" : "run";
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
            // FIXME
            e.printStackTrace();
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

    public static class Diagnostic {
        public String type;
        public String message;
        public String unknownPath;
        public models.Project project;
        public boolean projectClaim;
        public boolean missingChecksum;
        public String fileToChecksum;

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

        Import(String name, String version, ModuleVersion dep){
            this.name = name;
            this.version = version;
            this.existingDependency = dep;
        }

        Import(String name, String version, Module dep){
            this.name = name;
            this.version = version;
            this.newDependency = dep;
        }
    }

    public static class Module {
        public String[] authors;
        public String doc;
        public String license;
        public boolean jarChecksumValid;
        public boolean hasJarChecksum;
        public boolean hasJar;
        public List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        public String name;
        public String version;
        public String path;
        public boolean hasCar;
        public boolean hasJs;
        public boolean hasChecksum;
        public boolean checksumValid;
        public boolean jsChecksumValid;
        public boolean hasJsChecksum;
        public boolean hasSource;
        public boolean hasSourceChecksum;
        public boolean sourceChecksumValid;
        public boolean hasDocs;
        public int ceylonMajor;
        public int ceylonMinor;
        public List<Import> dependencies = new LinkedList<Import>();
        public boolean isRunnable;

        Module(String name, String version, String path){
            this.name = name;
            this.version = version;
            this.path = path;
        }

        public void addDependency(String name, String version, ModuleVersion dep) {
            dependencies.add(new Import(name, version, dep));
        }

        public void addDependency(String name, String version, Module dep) {
            dependencies.add(new Import(name, version, dep));
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
            return path.substring(1) + "module-doc" + "/" + "index.html";
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
