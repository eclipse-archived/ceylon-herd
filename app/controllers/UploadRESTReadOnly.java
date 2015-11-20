package controllers;

import java.io.File;
import java.io.IOException;

import models.Upload;
import models.User;
import play.Logger;
import play.data.validation.Validation;
import play.libs.MimeTypes;
import util.Util;

/**
 * This controller will serve files with no login but will require a user
 * and check that user for listing directories.
 */
public class UploadRESTReadOnly extends MyController {

    public static void viewFile(Long id, String path) throws IOException{
		models.Upload upload = getUpload(id);
		File uploadsDir = Util.getUploadDir(upload.id);
		File file = new File(uploadsDir, path);
		Uploads.checkUploadPath(file, uploadsDir);
		
		if(!file.exists())
			notFound(path);
		
		if(file.isDirectory()){
            if(!Util.isOnUiHost()){
                redirect(Util.viewPublicUploadUrl(upload, path, true));
            }
            // require user and check
            User user = requireUser();
            if(upload.owner != user && !user.isAdmin){
                Validation.addError(null, "You are not authorised to view this upload");
                prepareForErrorRedirect();
                Uploads.index();
            }
            render("Uploads/listFolder.html", upload, file);
		}else{
		    // public
            if(!Util.isOnDataHost()){
                notFound();
            }
            response.contentType = MimeTypes.getContentType(file.getName());
			renderBinary(file);
		}
	}

    private static User requireUser() {
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            if(user == null)
                forbidden();
            renderArgs.put("user", user);
            return user;
        }else{
            forbidden();
            return null;
        }
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
        return upload;
    }
}