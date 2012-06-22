package controllers;

import models.User;
import org.apache.commons.io.FileUtils;
import play.data.validation.Required;
import play.data.validation.Validation;
import util.ModuleChecker;
import util.ModuleChecker.Diagnostic;
import util.ModuleChecker.Import;
import util.ModuleChecker.Module;
import util.ModuleChecker.UploadInfo;
import util.MyCache;
import util.Util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Uploads extends LoggedInController {

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
		List<Diagnostic> diagnostics = ModuleChecker.collectModulesAndDiagnostics(uploadedFiles, modules, uploadsDir, user);
		
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

	static models.Upload getUpload(Long id) {
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
		List<Diagnostic> diagnostics = ModuleChecker.collectModulesAndDiagnostics(uploadedFiles, modules, uploadsDir, user);
		
		UploadInfo uploadInfo = new UploadInfo(upload, modules, diagnostics);
		
		String base = uploadsDir.getPath();
		render("Uploads/view.html", upload, uploadInfo, uploadedFiles, base);
	}

	public static void viewDoc(@Required Long id, @Required String moduleName, @Required String version){
        models.Upload upload = getUpload(id);
        String docPath = moduleName.replace('.', File.separatorChar)
                + File.separatorChar + version
                + File.separatorChar + "module-doc"
                + File.separatorChar + "index.html"; 
	    
	    render(upload, moduleName, version, docPath);
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
			modVersion.isCarPresent = module.hasCar;
			modVersion.isJsPresent = module.hasJs;
			modVersion.isSourcePresent = module.hasSource;
			modVersion.isAPIPresent = module.hasDocs;
			modVersion.published = Util.currentTimeInUTC();
			
			modVersion.create();

			for(Import imp : module.dependencies)
			    modVersion.addDependency(imp.name, imp.version, imp.optional, imp.export);
		}
		
		FileUtils.copyDirectory(uploadsDir, Util.getRepoDir(), NonEmptyDirectoryFilter);
		FileUtils.deleteDirectory(uploadsDir);
		upload.delete();

		MyCache.evictUploadsForOwner(user);
		
		flash("message", "Repository published");
		index();
	}
	
	public static void uploadRepoForm(Long id){
		models.Upload upload = Uploads.getUpload(id);
		render(upload);
	}
	
}