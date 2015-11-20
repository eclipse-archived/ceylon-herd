package controllers;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import models.Upload;
import models.User;
import play.Logger;
import play.libs.MimeTypes;
import play.mvc.Before;
import util.JavaExtensions;
import util.Util;

/**
 * This controller is for the CLI and tools, and requires to be on the data host and
 * a valid user/pass with Basic Auth. It does not initiate a session. 
 */
public class UploadRESTReadWrite extends MyController {

    @Before
	static void before(){
        // FIXME: require data host here
		Logger.info("UploadAPI [%s] %s %s", Security.connected(), request.method, request.path);
        // public
        if(!Util.isOnDataHost()){
            notFound();
        }
		String user = request.user;
		String password = request.password;
		if(!StringUtils.isEmpty(user)
				&& !StringUtils.isEmpty(password)){
			if(Security.authenticate(user, password)){
	            User userObject = User.findRegisteredByUserName(user);
	            renderArgs.put("user", userObject);
			}else{
				forbidden("Invalid user and/or password");
			}
		}else{ // required by most non-browser clients to force them to try basic auth
		    Logger.info("unauth");
			unauthorized();
		}
	}
	
    public static void viewFile(Long id, String path) throws IOException{
        models.Upload upload = getUpload(id);
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);
        Uploads.checkUploadPath(file, uploadsDir);
        
        if(!file.exists())
            notFound(path);
        
        if(file.isDirectory()){
            notFound(path);
        }else{
            response.contentType = MimeTypes.getContentType(file.getName());
            renderBinary(file);
        }
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
		models.Upload upload = getUpload(uploadId);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		Uploads.checkUploadPath(file, uploadsDir);
		
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
		models.Upload upload = getUpload(uploadId);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		Uploads.checkUploadPath(file, uploadsDir);
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
			Uploads.checkUploadPath(file, uploadsDir);
			if(file.isDirectory())
				error(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid path for upload: "+path+" (is a directory)");
			
			file.getParentFile().mkdirs();
			FileUtils.copyInputStreamToFile(request.body, file);
			request.body.close();
			
			// explode it if it's a module zip
			String name = file.getName().toLowerCase();
			if(name.equals("module-doc.zip") || name.equals("module-resources.zip")){
			    // this is both the pre-1.0 way where the doc archive was not yet created and the CMR zipped module-doc
			    // and the new post-1.1 way where the CMR takes care of automatically zipping folders
			    String folderName = name.substring(0, name.length() - 4);
			    File folderDir = new File(file.getParentFile(), folderName);
			    folderDir.mkdirs();
			    Uploads.uploadZip(file, null, folderDir);
			}else if(isDocArchive(path)){
			    // the >1.0 way has the CMR skip the module-doc folder and we extract it from the doc archive
                File moduleDoc = new File(file.getParentFile(), "module-doc");
                moduleDoc.mkdirs();
                Uploads.uploadZip(file, "api/", moduleDoc);
            }
			
			created();
		}
		error(HttpURLConnection.HTTP_BAD_REQUEST, "Empty file");
	}

	private static boolean isDocArchive(String path) {
	    if(!path.toLowerCase().endsWith(".doc.zip"))
	        return false;
	    int lastSlash = path.lastIndexOf('/');
	    if(lastSlash == -1)
	        return false;
	    String lastPart = path.substring(lastSlash+1, path.length()-8);
	    int sep = lastPart.indexOf('-');
	    String module;
	    String version = null;
	    if(sep == -1){
	        if(!lastPart.equals("default"))
	            return false;
	        module = lastPart;
	    }else{
	        module = lastPart.substring(0, sep);
            version = lastPart.substring(sep+1);
            if(module.isEmpty() || version.isEmpty())
                return false;
	    }
	    String prefix = "/" + module.replace('.', '/');
	    if(version != null)
	        prefix += "/" + version;
	    String match = prefix + "/" + lastPart + ".doc.zip";
	    return match.equals(path);
    }

    private static Upload getUpload(Long id) {
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
}