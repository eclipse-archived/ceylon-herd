package controllers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import models.ModuleVersion;
import models.User;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Before;
import util.Util;

public class Repo extends MyController {

	// we set it if it's there, otherwise we don't require it
	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.find("byUserName", Security.connected()).first();
            renderArgs.put("user", user);
        }
    }

	public static void index(){
		List<models.Module> modules = models.Module.all().fetch();
		
		render(modules);
	}

	public static void versions(@Required Long id){
		if(validationFailed()){
			index();
		}
		models.Module module = models.Module.findById(id);
		if(module == null){
			Validation.addError(null, "Unknown module");
			prepareForErrorRedirect();
			index();
		}
		List<models.ModuleVersion> versions = models.ModuleVersion.findByModule(module);
		
		render(module, versions);
	}

	public static void search(String q){
		if(StringUtils.isEmpty(q))
			index();
		
		List<models.Module> modules = models.Module.searchByName(q);
		
		render(modules, q);
	}

	public static void view(@Required Long moduleId, @Required Long versionId){
		models.ModuleVersion moduleVersion = getModuleVersion(versionId);
		models.Module module = moduleVersion.module;
		
		render(module, moduleVersion);
	}

	private static ModuleVersion getModuleVersion(Long id) {
		if(validationFailed())
			index();
		models.ModuleVersion moduleVersion = models.ModuleVersion.findById(id);
		if(moduleVersion == null){
			Validation.addError(null, "Unknown module");
			prepareForErrorRedirect();
			index();
		}
		return moduleVersion;
	}

	public static void viewFile(String path) throws IOException{
		File repoDir = Util.getRepoDir();
		File file = new File(repoDir, path);
		checkPath(file, repoDir);
		
		if(!file.exists())
			notFound(path);
		
		if(file.isDirectory())
			render("Repo/viewFile.html", file);
		else
			renderBinary(file);
	}

	public static void noFile() throws IOException{
	    render();
	}

	private static void checkPath(File file, File repoDir) throws IOException{
		String repoPath = repoDir.getCanonicalPath();
		String filePath = file.getCanonicalPath();
		if(!filePath.startsWith(repoPath))
			forbidden("Path is not valid");
	}
}