package util;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import models.User;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

public class ModuleChecker {

	public static List<Diagnostic> collectModulesAndDiagnostics(
			List<File> uploadedFiles, List<Module> modules, File uploadsDir, User user) {
		List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
		Map<String, File> fileByPath = new HashMap<String, File>();
		
		for(File f : uploadedFiles){
			String name = f.getName();
			String path = getPathRelativeTo(uploadsDir, f);
			fileByPath.put(path, f);
			if(name.endsWith(".car")){
				int sep = name.indexOf('-');
				if(sep == -1){
					if(name.equals("default.car"))
						diagnostics.add(new Diagnostic("error", "Default module not allowed."));
					else
						diagnostics.add(new Diagnostic("error", "Module car has no version: "+name));
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
				modules.add(new Module(module, version, path.substring(0, path.length()-name.length()), f));
			}
		}
		for(Module m : modules){
			checkModule(uploadsDir, fileByPath, m, user, modules);
		}
		if(modules.isEmpty())
			diagnostics.add(new Diagnostic("error", "No module defined"));
		if(!fileByPath.isEmpty()){
			for(String key : fileByPath.keySet())
				diagnostics.add(new Diagnostic("error", "Unknown file: "+key, key.substring(1)));
		}
		
		return diagnostics;
	}

	public static void checkModule(File uploadsDir,
			Map<String, File> fileByPath, Module m, User user, List<Module> modules) {
		models.Project project = models.Project.findOwner(m.name);
		if(project == null)
			m.diagnostics.add(new Diagnostic("error", "You do not own this module", project));
		else{
			// do we own it?
			if(project.owner == user)
				m.diagnostics.add(new Diagnostic("success", "You own this module"));
			else{
				// we don't own it but we may be admin
				models.Module publishedModule = models.Module.findByName(m.name);
				if(publishedModule == null || !publishedModule.canEdit(user))
					m.diagnostics.add(new Diagnostic("error", "You do not own this module", project));
				else
					m.diagnostics.add(new Diagnostic("success", "You are admin on this module"));
			}
		}
		
		models.ModuleVersion publishedModule = models.ModuleVersion.findByVersion(m.name, m.version);
		if(publishedModule != null)
			m.diagnostics.add(new Diagnostic("error", "Module already published"));
		
		String carName = m.name + "-" + m.version + ".car";
		fileByPath.remove(m.path + carName); // car

		m.diagnostics.add(new Diagnostic("success", "Has car: "+carName));

		String checksumPath = m.path + carName + ".sha1";
		m.hasChecksum = fileByPath.containsKey(checksumPath);
		if(m.hasChecksum){
			fileByPath.remove(checksumPath); // car checksum
			m.checksumValid = checkChecksum(uploadsDir, checksumPath, m.file);
			if(m.checksumValid)
				m.diagnostics.add(new Diagnostic("success", "Checksum valid"));
			else
				m.diagnostics.add(new Diagnostic("error", "Invalid checksum"));
		}else
			m.diagnostics.add(new Diagnostic("error", "Missing checksum"));

		loadModuleInfo(uploadsDir, m.path+carName, m, modules);

		String srcName = m.name + "-" + m.version + ".src";
		File srcFile = new File(uploadsDir, m.path + srcName);
		if(srcFile.exists()){
			m.hasSource = true;
			m.diagnostics.add(new Diagnostic("success", "Has source"));
			fileByPath.remove(m.path + srcName); // source archive
			String srcChecksumPath = m.path + srcName + ".sha1";
			m.hasSourceChecksum = fileByPath.containsKey(srcChecksumPath);
			if(m.hasSourceChecksum){
				fileByPath.remove(srcChecksumPath); // car checksum
				m.sourceChecksumValid = checkChecksum(uploadsDir, srcChecksumPath, srcFile);
				if(m.sourceChecksumValid)
					m.diagnostics.add(new Diagnostic("success", "Source checksum valid"));
				else
					m.diagnostics.add(new Diagnostic("error", "Invalid source checksum"));
			}else
				m.diagnostics.add(new Diagnostic("error", "Missing source checksum"));
		}else
			m.diagnostics.add(new Diagnostic("warning", "Missing source archive"));

		String docName = m.path + "module-doc" + File.separator + "index.html";
		File docFile = new File(uploadsDir, docName);
		if(docFile.exists()){
			m.hasDocs = true;
			m.diagnostics .add(new Diagnostic("success", "Has docs"));
			String prefix = m.path + "module-doc" + File.separator;
			Iterator<String> iterator = fileByPath.keySet().iterator();
			while(iterator.hasNext()){
				String key = iterator.next();
				// count all the doc files
				if(key.startsWith(prefix))
					iterator.remove();
			}
		}else
			m.diagnostics.add(new Diagnostic("warning", "Missing docs"));
	}

	private static void loadModuleInfo(File uploadsDir, String carName, Module m, List<Module> modules) {
		try {
			ZipFile car = new ZipFile(new File(uploadsDir, carName));
			
			try{
				ZipEntry moduleEntry = car.getEntry(m.name.replace('.', '/') + "/module.class");
				if(moduleEntry == null){
					m.diagnostics.add(new Diagnostic("error", ".car file does not contain module information"));
					return;
				}
				m.diagnostics.add(new Diagnostic("success", ".car file contains module descriptor"));

				DataInputStream inputStream = new DataInputStream(car.getInputStream(moduleEntry));
				ClassFile classFile = new ClassFile(inputStream);
				inputStream.close();

				AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag);
				Annotation moduleAnnotation = visible.getAnnotation("com.redhat.ceylon.compiler.java.metadata.Module");
				if(moduleAnnotation == null){
					m.diagnostics.add(new Diagnostic("error", ".car does not contain @Module annotation on module.class"));
					return;
				}
				m.diagnostics.add(new Diagnostic("success", ".car file module descriptor has @Module annotation"));

				String name = getString(moduleAnnotation, "name", m);
				String version = getString(moduleAnnotation, "version", m);
				if(name == null || version == null)
					return;
				if(!name.equals(m.name)){
					m.diagnostics.add(new Diagnostic("error", ".car file contains unexpected module: "+name));
					return;
				}
				if(!version.equals(m.version)){
					m.diagnostics.add(new Diagnostic("error", ".car file contains unexpected module version: "+version));
					return;
				}
				m.diagnostics.add(new Diagnostic("success", ".car file module descriptor has valid name/version"));
				
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
		String name = getString(dependency, "name", m);
		String version = getString(dependency, "version", m);
		if(name == null || version == null)
			return;
		if(name.isEmpty()){
			m.diagnostics.add(new Diagnostic("error", "Invalid empty dependency name"));
			return;
		}
		if(version.isEmpty()){
			m.diagnostics.add(new Diagnostic("error", "Invalid empty dependency version"));
			return;
		}
		
		MemberValue optionalValue = dependency.getMemberValue("optional");
		if(optionalValue == null){
			m.diagnostics.add(new Diagnostic("error", "Invalid @Import annotation: missing 'optional' value"));
			return;
		}
		if(!(optionalValue instanceof BooleanMemberValue)){
			m.diagnostics.add(new Diagnostic("error", "Invalid @Import 'optional' value (expecting boolean)"));
			return;
		}
		boolean optional = ((BooleanMemberValue)optionalValue).getValue();
		if(optional){
			m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" is optional"));
			return;
		}
		// must make sure it exists
		checkDependencyExists(name, version, m, modules);
	}

	private static void checkDependencyExists(String name, String version,
			Module m, List<Module> modules) {
		// try to find it in the list of uploaded modules
		for(Module module : modules){
			if(module.name.equals(name) && module.version.equals(version)){
				m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" is to be uploaded"));
				return;
			}
		}
		// try to find it in the repo
		models.ModuleVersion dep = models.ModuleVersion.find("name = ? AND version = ?", name, version).first();
		if(dep == null){
			m.diagnostics.add(new Diagnostic("error", "Dependency "+name+"/"+version+" cannot be found in upload or repo"));
		}else{
			m.diagnostics.add(new Diagnostic("success", "Dependency "+name+"/"+version+" present in repo"));
		}
	}

	private static String getString(Annotation annotation,
			String field, Module m) {
		MemberValue value = annotation.getMemberValue(field);
		if(value == null){
			m.diagnostics.add(new Diagnostic("error", "Missing '"+field+"' annotation value"));
			return null;
		}
		if(!(value instanceof StringMemberValue)){
			m.diagnostics.add(new Diagnostic("error", "Invalid '"+field+"' annotation value (expecting String)"));
			return null;
		}
		return ((StringMemberValue)value).getValue();
	}

	private static boolean checkChecksum(File uploadsDir, String checksumPath, File checkedFile) {
		File checksumFile = new File(uploadsDir, checksumPath);
		try{
			String checksum = FileUtils.readFileToString(checksumFile);
			InputStream is = new FileInputStream(checkedFile);
			try{
				String realChecksum = DigestUtils.shaHex(is);
				return realChecksum.equals(checksum);
			}finally{
				is.close();
			}
		}catch(Exception x){
			return false;
		}
	}

	private static String getPathRelativeTo(File uploadsDir, File f) {
		try{
			String prefix = uploadsDir.getCanonicalPath();
			String path = f.getCanonicalPath();
			if(path.startsWith(prefix))
				return path.substring(prefix.length());
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

	public static class Module {
		public List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
		public String name;
		public String version;
		public String path;
		public File file;
		public boolean hasChecksum;
		public boolean checksumValid;
		public boolean hasSource;
		public boolean hasSourceChecksum;
		public boolean sourceChecksumValid;
		public boolean hasDocs;
		
		Module(String name, String version, String path, File file){
			this.name = name;
			this.version = version;
			this.path = path;
			this.file = file;
		}
		
		public String getType(){
			String worse = "success";
			for(Diagnostic d : diagnostics){
				if(d.type.equals("error"))
					return d.type;
				if(d.type.equals("warning"))
					worse = d.type;
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
				if(d.type.equals("warning"))
					status = d.type;
			}
			for(Module m : modules){
				String type = m.getType();
				if(type.equals("error")){
					status = type;
					return;
				}
				if(type.equals("warning"))
					status = type;
			}
		}
		
		public boolean isPublishable(){
			return status.equals("success") || status.equals("warning"); 
		}
	}
}
