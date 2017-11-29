/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package controllers;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import models.Author;
import models.HerdDependency;
import models.MavenDependency;
import models.Upload;
import models.User;
import play.Logger;
import play.Play;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.mvc.Scope;
import util.JavaExtensions;
import util.ModuleChecker;
import util.ModuleChecker.Diagnostic;
import util.ModuleChecker.Import;
import util.ModuleChecker.Module;
import util.ModuleChecker.UploadInfo;
import util.ModuleSpec;
import util.MyCache;
import util.Util;

public class Uploads extends LoggedInController {

    private static final String[] SupportedExtensions = new String[]{".car", ".jar", ".src", ".js"};

	private static final FileFilter NonEmptyDirectoryFilter = new FileFilter(){
		@Override
		public boolean accept(File f) {
			if(!f.isDirectory())
				return true;
			for(File child : f.listFiles()){
				if(accept(child))
					return true;
			}
			return false;
		}
	};


	public static void index() {
    	User user = getUser();
    	List<models.Upload> uploads = user.uploads;
    	List<UploadInfo> uploadInfos = new ArrayList<UploadInfo>(uploads.size());
    	for(models.Upload upload : uploads){
    		File uploadsDir = Util.getUploadDir(upload.id);
    		
    		UploadInfo uploadInfo = getUploadInfo(upload, uploadsDir, user);

    		uploadInfos.add(uploadInfo);
    	}
        render(uploadInfos);
    }

	private static UploadInfo getUploadInfo(models.Upload upload,
			File uploadsDir, User user) {
		List<File> uploadedFiles = new ArrayList<File>();
		collectFiles(uploadsDir, uploadedFiles);

		List<Module> modules = new ArrayList<Module>();
		List<Diagnostic> diagnostics = ModuleChecker.collectModulesAndDiagnostics(uploadedFiles, modules, uploadsDir, user, upload);
		
		return new UploadInfo(upload, modules, diagnostics);
	}

	public static void newUpload() throws IOException {
		models.Upload upload = new models.Upload();
		upload.owner = getUser();
		upload.created = Util.currentTimeInUTC();
		upload.create();
		File uploadDir = Util.getUploadDir(upload.id);
		if(!uploadDir.mkdirs())
			throw new RuntimeException("Failed to create upload dir "+uploadDir.getAbsolutePath());
        
		MyCache.evictUploadsForOwner(upload.owner);

        view(upload.id);
	}
	
	private static void collectFiles(File file, List<File> uploadedFiles) {
		if(file.isDirectory()){
			for(File child : file.listFiles()){
				collectFiles(child, uploadedFiles);
			}
		}else
			uploadedFiles.add(file);
	}

	private static models.Upload getUpload(Long id) {
		if(id == null){
			Validation.addError(null, "Missing upload id");
			prepareForErrorRedirect();
			index();
		}
		models.Upload upload = models.Upload.findById(id);
		if(upload == null){
			Validation.addError(null, "Invalid upload id");
			prepareForErrorRedirect();
			index();
		}
		User user = getUser();
		if(upload.owner != user && !user.isAdmin){
			Validation.addError(null, "You are not authorised to view this upload");
			prepareForErrorRedirect();
			index();
		}
		return upload;
	}

	public static void view(Long id) throws IOException {
		models.Upload upload = getUpload(id);
		User user = getUser();
		File uploadsDir = Util.getUploadDir(id);
		List<File> uploadedFiles = new ArrayList<File>();
		collectFiles(uploadsDir, uploadedFiles);

		List<Module> modules = new ArrayList<Module>();
		List<Diagnostic> diagnostics = ModuleChecker.collectModulesAndDiagnostics(uploadedFiles, modules, uploadsDir, user, upload);
		
		UploadInfo uploadInfo = new UploadInfo(upload, modules, diagnostics);
		
		String base = uploadsDir.getPath();
		render("Uploads/view.html", upload, uploadInfo, uploadedFiles, base);
	}

	public static void removeHerdDependency(Long id, String name, String version) throws IOException {
	    models.Upload upload = getUpload(id);

	    HerdDependency hd = getHerdDependency(upload, name, version);
	    if(hd == null){
	        Validation.addError(null, "Module was not resolved from Maven");
	        prepareForErrorRedirect();
	        view(id);
	    }
	    hd.delete();
	    view(upload.id);
	}

	public static void removeMavenDependency(Long id, String name, String version) throws IOException {
	    models.Upload upload = getUpload(id);

	    MavenDependency md = getMavenDependency(upload, name, version);
	    if(md == null){
	        Validation.addError(null, "Module was not resolved from Maven");
	        prepareForErrorRedirect();
	        view(id);
	    }
	    md.delete();
	    view(upload.id);
	}

	public static void resolveMavenDependency(Long id, String name, String version) throws IOException {
	    models.Upload upload = getUpload(id);

	    MavenDependency md = getMavenDependency(upload, name, version);
        // check that we didn't already resolve it from Maven
        if(md != null){
            Validation.addError(null, "Module already resolved from Maven");
            prepareForErrorRedirect();
            view(id);
        }
        
	    // check if the module in question is really in maven
	    // ex: http://repo1.maven.org/maven2/io/vertx/vertx-core/2.0.0-beta5/vertx-core-2.0.0-beta5.jar
	    int idSep = name.lastIndexOf(':');
	    if(idSep == -1)
	        idSep = name.lastIndexOf('.');
	    if(idSep == -1){
	        Validation.addError(null, "Module name does not contain any ':' or '.' (used to separate the artifact group and ID)");
            prepareForErrorRedirect();
            view(id);
	    }
        String mavenUrl = Play.configuration.getProperty("maven.url", "http://repo1.maven.org/maven2");
	    String groupId = name.substring(0, idSep);
        String groupIdPath = groupId.replace('.', '/');
	    String artifactId = name.substring(idSep+1);
	    String url = mavenUrl + "/" + groupIdPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
	    
	    Logger.info("Looking up module in Maven Central at: %s", url);
	    HttpResponse response = WS.url(url).head();
	    if(response.getStatus() == HttpURLConnection.HTTP_OK){
	        md = new MavenDependency(name, version, upload);
	        md.create();
	        flash("message", "Found in Maven central");
        }else if(response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND){
            flash("message", "Module could not be found in Maven central");
	    }else{
	        flash("message", "Module could not be found in Maven central: " + response.getStatus() + ": "+response.getString());
	    }
	    
	    view(id);
	}
	
	// TODO:  Make this more decoupled and unit testable
	public static void resolveHerdDependency(Long id, String name, String version) throws IOException {
	    models.Upload upload = getUpload(id);

	    HerdDependency hd = getHerdDependency(upload, name, version);
        // check that we didn't already resolve it from Herd
        if(hd != null){
            Validation.addError(null, "Module already resolved from Herd");
            prepareForErrorRedirect();
            view(id);
        }
        
	    String url = "http://modules.ceylon-lang.org/modules/" + name + "/" + version;
	    
	    Logger.info("Looking up module in Herd at: %s", url);
	    HttpResponse response = WS.url(url).head();
	    if(response.getStatus() == HttpURLConnection.HTTP_OK){
	        hd = new HerdDependency(name, version, upload);
	        hd.create();
	        flash("message", "Found in Herd");
        }else if(response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND){
            flash("message", "Module could not be found in Herd");
	    }else{
	        flash("message", "Module could not be found in Herd: " + response.getStatus() + ": "+response.getString());
	    }
	    
	    view(id);
	}
	
	public static void clearMavenDependencies(Long id) throws IOException{
	    models.Upload upload = getUpload(id);
	    for(MavenDependency md : upload.mavenDependencies)
	        md.delete();
	    flash("message", "Maven dependencies cleared");
	    view(id);
	}

	public static void clearHerdDependencies(Long id) throws IOException{
	    models.Upload upload = getUpload(id);
	    for(HerdDependency hd : upload.herdDependencies)
	        hd.delete();
	    flash("message", "Maven dependencies cleared");
	    view(id);
	}

	private static MavenDependency getMavenDependency(Upload upload,
            String name, String version) throws IOException {
        if(name == null || name.isEmpty()){
            Validation.addError(null, "Empty name");
        }
        if(version == null || version.isEmpty()){
            Validation.addError(null, "Empty version");
        }
        if(Validation.hasErrors()){
            prepareForErrorRedirect();
            view(upload.id);
        }
        return upload.findMavenDependency(name, version);
    }

	private static HerdDependency getHerdDependency(Upload upload, String name, String version) 
    throws IOException {
        if(name == null || name.isEmpty()){
            Validation.addError(null, "Empty name");
        }
        if(version == null || version.isEmpty()){
            Validation.addError(null, "Empty version");
        }
        if(Validation.hasErrors()){
            prepareForErrorRedirect();
            view(upload.id);
        }
        return upload.findHerdDependency(name, version);
    }

    public static void viewDoc(@Required Long id, @Required String moduleName, @Required String version){
        models.Upload upload = getUpload(id);
        String docPath = moduleName.replace('.', File.separatorChar)
                + File.separatorChar + version
                + File.separatorChar + "module-doc"
                + File.separatorChar + "api"
                + File.separatorChar + "index.html"; 
	    
        redirect(Util.viewPublicUploadUrl(upload, docPath));
	}

	public static void delete(Long id) throws IOException {
		models.Upload upload = getUpload(id);
		File uploadsDir = Util.getUploadDir(id);
		
		upload.delete();
		FileUtils.deleteDirectory(uploadsDir);

		MyCache.evictUploadsForOwner(upload.owner);

		flash("message", "Upload repository deleted");
		index();
	}

	public static void publish(Long id) throws IOException {
		models.Upload upload = getUpload(id);
		File uploadsDir = Util.getUploadDir(id);
		User user = getUser();
		UploadInfo uploadInfo = getUploadInfo(upload, uploadsDir, user);
		
		if(!uploadInfo.isPublishable()){
			Validation.addError(null, "Upload is not valid, cannot publish. Fix errors first.");
			prepareForErrorRedirect();
			view(id);
		}
		
		List<models.ModuleVersion> versions = new LinkedList<models.ModuleVersion>();
		for(Module module : uploadInfo.modules){
			models.Module mod = models.Module.find("name = ?", module.name).first();
			if(mod == null){
				mod = new models.Module();
				mod.name = module.name;
				mod.owner = user;
				mod.create();
			}

			models.ModuleVersion modVersion = new models.ModuleVersion();
			modVersion.module = mod;
			modVersion.version = module.version;
			modVersion.isCarPresent = module.car.exists;
			modVersion.isJarPresent = module.jar.exists;
			modVersion.isJsPresent = module.js.exists;
			modVersion.isSourcePresent = module.source.exists;
            modVersion.isScriptsPresent = module.scripts.exists;
			modVersion.isAPIPresent = module.docs.hasUnzipped;
			modVersion.isDocPresent = module.docs.exists;
            modVersion.isResourcesPresent = module.docs.exists;
            modVersion.isRunnable = module.isRunnable;
			modVersion.jvmBinMajor = module.jvmBinMajor;
            modVersion.jvmBinMinor = module.jvmBinMinor;
            modVersion.jsBinMajor = module.jsBinMajor;
            modVersion.jsBinMinor = module.jsBinMinor;
            modVersion.isNativeJvm = module.isNativeJvm();
            modVersion.isNativeJs = module.isNativeJs();
			modVersion.published = Util.currentTimeInUTC();
			modVersion.doc = module.doc;
			modVersion.license = module.license;
			if(module.authors != null){
			    for(String author : module.authors){
			        modVersion.authors.add(Author.findOrCreate(author));
			    }
			}
			modVersion.isPackageJsonPresent = module.jsPackage;
			modVersion.groupId = module.groupId;
			modVersion.artifactId = module.artifactId;
			modVersion.create();

			for(Import imp : module.getAllDependencies())
			    modVersion.addDependency(imp.name, imp.version, imp.optional, imp.export, 
			            imp.mavenDependency != null, imp.herdDependency != null,
			            imp.isNativeJvm(), imp.isNativeJs());
			
			for(ModuleChecker.Member member : module.members)
			    modVersion.addMember(member.packageName, member.name, member.type, member.shared);

			for(ModuleChecker.Script script : module.scriptDescriptions)
                modVersion.addScript(script.name, script.description, script.unix, script.plugin, script.module);
			
			versions.add(modVersion);
		}
		
		FileUtils.copyDirectory(uploadsDir, Util.getRepoDir(), NonEmptyDirectoryFilter);
		FileUtils.deleteDirectory(uploadsDir);
		upload.delete();

		MyCache.evictUploadsForOwner(user);
		MyCache.evictModulesForOwner(user);
		
		if(versions.size() == 1){
	        flash("message", "Your module has been published");
		    models.ModuleVersion moduleVersion = versions.get(0);
		    Repo.view(moduleVersion.module.name, moduleVersion.version);
		}else{
            flash("message", "Your modules have been published");
		    render(versions);
		}
	}
	
	public static void uploadRepoForm(Long id){
		models.Upload upload = Uploads.getUpload(id);
		render(upload);
	}

    public static void deleteFile(Long id, String path, boolean returnToBrowse) throws IOException {
        models.Upload upload = Uploads.getUpload(id);
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);

        deleteFileImpl(path, file, uploadsDir, upload);

        if (returnToBrowse) {
            File parent = file.getParentFile();
            String parentPath = JavaExtensions.relativeTo(parent, upload);
            // if we do viewFile directly we get silly %2F escapes in the URL
            redirect(Util.viewPublicUploadUrl(upload, parentPath));
        } else {
            Uploads.view(id);
        }
    }

    public static void deleteFileAsync(Long id, String path) throws IOException {
        models.Upload upload = Uploads.getUpload(id);
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);

        deleteFileImpl(path, file, uploadsDir, upload);

        String message = Scope.Flash.current().get("message");
        renderJSON("{\"message\":\"" + message + "\"}");
    }

    private static void deleteFileImpl(String path, File file, File uploadsDir, Upload upload) throws IOException {
        checkUploadPath(file, uploadsDir);

        if (!file.exists()) {
            notFound(path);
        }

        Logger.info("delete: %s exists: %s", path, file.exists());

        if (uploadsDir.getCanonicalPath().equals(file.getCanonicalPath())) {
            // clear the entire repo
            for (File f : uploadsDir.listFiles()) {
                if (f.isDirectory())
                    FileUtils.deleteDirectory(f);
                else
                    f.delete();
            }
            // let's be helpful and remove maven dependencies too
            for (MavenDependency md : upload.mavenDependencies) {
                md.delete();
            }
            flash("message", "Upload cleared");
        } else if (file.isDirectory()) {
            FileUtils.deleteDirectory(file);
            flash("message", "Directory deleted");
        } else {
            file.delete();
            flash("message", "File deleted");
        }
    }
    
    public static void addChecksum(Long id, String path) throws IOException{
        models.Upload upload = Uploads.getUpload(id);
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);
        checkUploadPath(file, uploadsDir);

        if(!file.exists())
            notFound(path);

        Logger.info("add checksum: %s exists: %s", path, file.exists());
        
        String sha1 = ModuleChecker.sha1(file);
        File sha1File = new File(uploadsDir, path+".sha1");
        FileUtils.write(sha1File, sha1);
        
        Uploads.view(id);
    }

    public static void uploadRepo(Long id, @Required File repo, String module, String version) throws ZipException, IOException{
        models.Upload upload = Uploads.getUpload(id);
        File uploadsDir = Util.getUploadDir(upload.id);

        if(validationFailed()){
            Uploads.uploadRepoForm(id);
        }
        
        String name = repo.getName().toLowerCase();
        
        if(name.endsWith(".zip"))
            uploadZip(repo, null, uploadsDir);
        else{
            boolean found = false;
            for(String postfix : SupportedExtensions){
                if(name.endsWith(postfix)){
                    uploadArchive(repo, postfix, uploadsDir, module, version, id);
                    found = true;
                    break;
                }
            }
            if(!found)
                error(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid uploaded file (must be zip, car, jar or src)");
        }
        
        
        Uploads.view(id);
    }

    private static void uploadArchive(File file, String postfix, File uploadsDir, String module, String version, Long uploadId) throws IOException {
        try{
            // parse unless it's a jar and we have alternate info
            ModuleSpec spec;
            Logger.info("postfix: %s, module: %s, version: %s", postfix, module, version);
            if(postfix.equals(".jar") && (!StringUtils.isEmpty(module) || !StringUtils.isEmpty(version))){
                Validation.required("module", module);
                if("default".equals(module))
                    Validation.addError("module", "Default module not allowed");
                Validation.required("version", version);
                if(validationFailed()){
                    Uploads.uploadRepoForm(uploadId);
                }
                spec = new ModuleSpec(module, version);
            }else{
                spec = ModuleSpec.parse(file.getName(), postfix);
            }
            
            // build dest file
            String path = spec.name.replace('.', File.separatorChar) 
                    + File.separatorChar + spec.version 
                    + File.separatorChar + spec.name + '-' + spec.version + postfix;
            File destFile = new File(uploadsDir, path);
            
            // check
            checkUploadPath(destFile, uploadsDir);
            if(destFile.isDirectory())
                error(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid path for upload: "+destFile.getName()+" (is a directory)");
            
            // all good, let's copy
            destFile.getParentFile().mkdirs();
            FileUtils.copyFile(file, destFile);
            
            // now let's sha1 it
            String sha1 = ModuleChecker.sha1(destFile);
            File sha1File = new File(uploadsDir, path+".sha1");
            FileUtils.write(sha1File, sha1);
            
            flash("message", "Uploaded archive file");
        }catch(ModuleSpec.ModuleSpecException x){
            error(HttpURLConnection.HTTP_BAD_REQUEST, x.getMessage());
        }
    }

    static void uploadZip(File repo, String prefixFilter, File uploadsDir) throws ZipException, IOException {
        ZipFile zip = new ZipFile(repo);
        try{
            // first check them all
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                // skip directories
                if(entry.isDirectory())
                    continue;
                if(prefixFilter != null && !entry.getName().toLowerCase().startsWith(prefixFilter))
                    continue;
                File file = new File(uploadsDir, entry.getName());
                checkUploadPath(file, uploadsDir);
                if(file.isDirectory())
                    error(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid path for upload: "+entry.getName()+" (is a directory)");
            }
            // then store
            entries = zip.entries();
            int files = 0;
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                // skip directories
                if(entry.isDirectory())
                    continue;
                String targetName = entry.getName();
                if(prefixFilter != null){
                    if(!targetName.toLowerCase().startsWith(prefixFilter))
                        continue;
                    targetName = targetName.substring(prefixFilter.length());
                }
                File file = new File(uploadsDir, targetName);

                files++;
                file.getParentFile().mkdirs();
                InputStream inputStream = zip.getInputStream(entry);
                try{
                    FileUtils.copyInputStreamToFile(inputStream, file);
                }finally{
                    inputStream.close();
                }
            }
            flash("message", "Uploaded "+files+" file"+(files>1 ?"s":""));
        }finally{
            zip.close();
        }
    }

    static void checkUploadPath(File file, File uploadsDir) throws IOException{
        String uploadsPath = uploadsDir.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        if(!filePath.startsWith(uploadsPath))
            forbidden("Path is not in your uploads repository");
    }

    public static void listFolder(Long id, String path) throws IOException{
        models.Upload upload = getUpload(id);
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);
        Uploads.checkUploadPath(file, uploadsDir);
        
        if(!file.exists())
            notFound(path);
        
        if(file.isDirectory()){
            render("Uploads/listFolder.html", upload, file);
        }else{
            notFound();
        }
    }
}