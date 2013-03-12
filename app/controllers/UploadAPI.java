package controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import models.Upload;
import models.User;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Before;
import play.mvc.Http.Header;
import util.JavaExtensions;
import util.ModuleChecker;
import util.ModuleSpec;
import util.Util;

public class UploadAPI extends LoggedInController {

	private static final String[] SupportedExtensions = new String[]{".car", ".jar", ".src", ".js"};

    @Before
	static void before(){
		Logger.info("UploadAPI [%s] %s %s", Security.connected(), request.method, request.path);
		String user = request.user;
		String password = request.password;
		if(Security.isConnected())
			return;
		if(!StringUtils.isEmpty(user)
				&& !StringUtils.isEmpty(password)){
			Logger.info("Try auth");
			if(Security.authenticate(user, password)){
				Logger.info("OK");
				session.put("username", user);
			}else{
				Logger.info("Failed");
				forbidden("Invalid user and/or password");
			}
		}else // required by most non-browser clients to force them to try basic auth
			unauthorized();
	}
	
	public static void dispatch(Long id, String path) throws IOException{
		if("MKCOL".equals(request.method))
			mkdir(id, path);
		else if("LOCK".equals(request.method))
			lock(id, path);
		else if("UNLOCK".equals(request.method))
			unlock(id, path);
		else if("PROPFIND".equals(request.method))
			propfind(id, path);
		badRequest();
	}

	//
	// Stupid DAV shit

	private static void propfind(Long uploadId, String path) throws IOException {
		models.Upload upload = Uploads.getUpload(uploadId);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		checkUploadPath(file, uploadsDir);
		
		if(!file.exists())
			notFound();

		StringBuilder ret = new StringBuilder("<?xml version='1.0' encoding='utf-8' ?>\n");
		ret.append("<multistatus xmlns='DAV:'>\n");
		if(file.isFile())
			propfindFile(file, upload, ret);
		else{
			// first one is the folder itself
			propfindFile(file, upload, ret);
			for(File child : file.listFiles())
				propfindFile(child, upload, ret);
		}
		ret.append("</multistatus>\n");
		response.status = 207; // MULTI STATUS
		renderXml(ret.toString());
	}
	
	private static void propfindFile(File file, models.Upload upload, StringBuilder xml) {
		String path = JavaExtensions.relativeTo(file, upload);
		xml.append("<response>\n");
		xml.append("  <href>/").append(path).append("</href>\n");
		xml.append("  <propstat>\n");
		xml.append("    <prop>\n");
		if(file.isDirectory())
			xml.append("      <resourcetype><collection/></resourcetype>\n");
		else
			xml.append("      <resourcetype/>\n");
		xml.append("    </prop>\n");
		xml.append("    <status>HTTP/1.1 200 OK</status>\n");
		xml.append("  </propstat>\n");
		xml.append("</response>\n");
	}

	private static void unlock(Long uploadId, String path) {
		noContent();
	}

	private static void lock(Long uploadId, String path) throws IOException {
		renderXml("<?xml version='1.0' encoding='utf-8' ?>\n"
+"<prop xmlns='DAV:'>\n"
+"  <lockdiscovery>\n"
+"    <activelock>\n"
+"      <locktype><write/></locktype>\n"
+"      <lockscope><exclusive/></lockscope>\n"
+"      <depth>infinity</depth>\n"
+"      <owner>\n"
+"        <href>FOO</href>\n"
+"      </owner>\n"
+"      <timeout>Second-604800</timeout>\n"
+"      <locktoken>\n"
+"        <href>urn:uuid:e71d4fae-5dec-22d6-fea5-00a0c91e6be4</href>\n"
+"      </locktoken>\n"
+"    </activelock>\n"
+"  </lockdiscovery>\n"
+"</prop>\n"
);
	}

	private static void mkdir(Long uploadId, String path) throws IOException{
		models.Upload upload = Uploads.getUpload(uploadId);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		checkUploadPath(file, uploadsDir);
		if(file.isFile())
			error(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid path for MKCOL: "+path+" (is an existing file)");
			
		file.mkdirs();
		ok();
	}


	public static void addFile(Long id, String path) throws IOException{
		models.Upload upload = getUpload(id);
		if(request.body.available() > 0){
			File uploadsDir = Util.getUploadDir(upload.id);
			File file = new File(uploadsDir, path);
			checkUploadPath(file, uploadsDir);
			if(file.isDirectory())
				error(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid path for upload: "+path+" (is a directory)");
			
			file.getParentFile().mkdirs();
			FileUtils.copyInputStreamToFile(request.body, file);
			request.body.close();
			
			// explode it if it's a module zip
			if(file.getName().equals("module-doc.zip")){
			    File moduleDoc = new File(file.getParentFile(), "module-doc");
			    moduleDoc.mkdirs();
			    uploadZip(file, moduleDoc);
			    file.delete();
			}
			
			created();
		}
		error(HttpURLConnection.HTTP_BAD_REQUEST, "Empty file");
	}

	private static Upload getUpload(Long id) {
	    if(isInteractive())
	        return Uploads.getUpload(id);
	    if(id == null){
	        Logger.info("Missing upload id");
	        badRequest("Missing upload id");
	    }
	    models.Upload upload = models.Upload.findById(id);
	    if(upload == null){
            Logger.info("Invalid upload id");
	        notFound("Invalid upload id");
	    }
	    User user = getUser();
	    if(upload.owner != user && !user.isAdmin){
            Logger.info("Not authorised");
	        badRequest("You are not authorised to view this upload");
	    }
	    return upload;
    }

    private static boolean isInteractive() {
        Header userAgent = request.headers.get("user-agent");
        if(userAgent == null || userAgent.values.isEmpty()){
            Logger.info("No UA 1: interactive");
            return true;
        }
        String ua = userAgent.values.get(0);
        if(ua == null){
            Logger.info("No UA 2: interactive");
            return true;
        }
        Logger.info("UA: %s", ua);
        return !ua.startsWith("Java") && !ua.startsWith("Sardine");
    }

    public static void viewFile(Long id, String path) throws IOException{
		models.Upload upload = getUpload(id);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		checkUploadPath(file, uploadsDir);
		
		if(!file.exists())
			notFound(path);
		
		if(file.isDirectory())
			render("Uploads/viewFile.html", upload, file);
		else
			renderBinary(file);
	}

	private static void checkUploadPath(File file, File uploadsDir) throws IOException{
		String uploadsPath = uploadsDir.getCanonicalPath();
		String filePath = file.getCanonicalPath();
		if(!filePath.startsWith(uploadsPath))
			forbidden("Path is not in your uploads repository");
	}
	
	public static void deleteFile(Long id, String path, boolean returnToBrowse) throws IOException{
		models.Upload upload = Uploads.getUpload(id);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		checkUploadPath(file, uploadsDir);

		if(!file.exists())
			notFound(path);

		Logger.info("delete: %s exists: %s", path, file.exists());
		
        if(uploadsDir.getCanonicalPath().equals(file.getCanonicalPath())){
            // clear the entire repo
            for(File f : uploadsDir.listFiles()){
                if(f.isDirectory())
                    FileUtils.deleteDirectory(f);
                else
                    f.delete();
            }
            flash("message", "Upload cleared");
        }else if(file.isDirectory()){
			FileUtils.deleteDirectory(file);
			flash("message", "Directory deleted");
		}else{
			file.delete();
			flash("message", "File deleted");
		}
		if(returnToBrowse){
			File parent = file.getParentFile();
			String parentPath = JavaExtensions.relativeTo(parent, upload);
			viewFile(upload.id, parentPath);
		}else
			Uploads.view(id);
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
		    uploadZip(repo, uploadsDir);
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

    private static void uploadZip(File repo, File uploadsDir) throws ZipException, IOException {
        ZipFile zip = new ZipFile(repo);
        try{
            // first check them all
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                // skip directories
                if(entry.isDirectory())
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
                File file = new File(uploadsDir, entry.getName());

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
}