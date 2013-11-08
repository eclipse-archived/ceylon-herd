package controllers;

import models.Author;
import models.MavenDependency;
import models.Upload;
import models.User;
import org.apache.commons.io.FileUtils;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.WS;
import play.libs.WS.HttpResponse;
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
import java.net.HttpURLConnection;
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
		List<Diagnostic> diagnostics = ModuleChecker.collectModulesAndDiagnostics(uploadedFiles, modules, uploadsDir, user, upload);
		
		UploadInfo uploadInfo = new UploadInfo(upload, modules, diagnostics);
		
		String base = uploadsDir.getPath();
		render("Uploads/view.html", upload, uploadInfo, uploadedFiles, base);
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
	    String namePath = name.replace('.', '/');
	    int idSep = name.lastIndexOf('.');
	    if(idSep == -1){
	        Validation.addError(null, "Module name does not contain any '.' (used to separate the artifact group and ID)");
            prepareForErrorRedirect();
            view(id);
	    }
	    String idPart = name.substring(idSep+1);
	    String url = "http://repo1.maven.org/maven2/" + namePath + "/" + version + "/" + idPart + "-" + version + ".jar";
	    
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
	
	public static void clearMavenDependencies(Long id) throws IOException{
	    models.Upload upload = getUpload(id);
	    for(MavenDependency md : upload.mavenDependencies)
	        md.delete();
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
			modVersion.isJarPresent = module.hasJar;
			modVersion.isJsPresent = module.hasJs;
			modVersion.isSourcePresent = module.hasSource;
			modVersion.isAPIPresent = module.hasDocs;
			modVersion.isDocPresent = module.hasDocArchive;
            modVersion.isRunnable = module.isRunnable;
			modVersion.ceylonMajor = module.ceylonMajor;
            modVersion.ceylonMinor = module.ceylonMinor;
			modVersion.published = Util.currentTimeInUTC();
			modVersion.doc = module.doc;
			modVersion.license = module.license;
			if(module.authors != null){
			    for(String author : module.authors){
			        modVersion.authors.add(Author.findOrCreate(author));
			    }
			}
			modVersion.create();

			for(Import imp : module.dependencies)
			    modVersion.addDependency(imp.name, imp.version, imp.optional, imp.export, imp.mavenDependency != null);
			for(ModuleChecker.Member member : module.members)
			    modVersion.addMember(member.packageName, member.name, member.type);
		}
		
		FileUtils.copyDirectory(uploadsDir, Util.getRepoDir(), NonEmptyDirectoryFilter);
		FileUtils.deleteDirectory(uploadsDir);
		upload.delete();

		MyCache.evictUploadsForOwner(user);
		MyCache.evictModulesForOwner(user);
		
		flash("message", "Repository published");
		index();
	}
	
	public static void uploadRepoForm(Long id){
		models.Upload upload = Uploads.getUpload(id);
		render(upload);
	}
}