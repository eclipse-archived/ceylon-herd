package controllers;

import java.io.File;
import java.io.IOException;

import models.Module;
import models.ModuleVersion;
import models.User;

import org.apache.commons.io.FileUtils;

import play.data.validation.Required;
import play.data.validation.Validation;
import util.Util;

public class LoggedInRepo extends LoggedInController {

	private static ModuleVersion getModuleVersion(Long id) {
		if(validationFailed())
			Repo.index();
		models.ModuleVersion moduleVersion = models.ModuleVersion.findById(id);
		if(moduleVersion == null){
			Validation.addError(null, "Unknown module version");
			prepareForErrorRedirect();
			Repo.index();
		}
		return moduleVersion;
	}

	private static Module getModule(Long id) {
		if(validationFailed())
			Repo.index();
		models.Module module = models.Module.findById(id);
		if(module == null){
			Validation.addError(null, "Unknown module");
			prepareForErrorRedirect();
			Repo.index();
		}
		return module;
	}

	public static void editForm(@Required Long id){
		Module module = getModule(id);
		User user = getUser();
		if(!user.isAdmin && module.owner != user){
			Validation.addError(null, "Unauthorised");
			prepareForErrorRedirect();
			Repo.index();
		}
		
		render(module);
	}

	public static void edit(@Required Long id, String url, String issues, String code, String friendlyName){
		Module module = getModule(id);
		User user = getUser();
		if(!user.isAdmin && module.owner != user){
			Validation.addError(null, "Unauthorised");
			prepareForErrorRedirect();
			Repo.index();
		}
		
		module.codeURL = code;
		module.homeURL = url;
		module.issueTrackerURL = issues;
		module.friendlyName = friendlyName;
		module.save();
		
		Repo.versions(module.id);
	}

	@Check("admin")
	public static void remove1(@Required Long moduleId, @Required Long versionId){
		ModuleVersion moduleVersion = getModuleVersion(versionId);
		Module module = moduleVersion.module;
		
		render(module, moduleVersion);
	}
	
	@Check("admin")
	public static void remove2(@Required Long moduleId, @Required Long versionId){
		ModuleVersion moduleVersion = getModuleVersion(versionId);
		Module module = moduleVersion.module;
		
		render(module, moduleVersion);
	}
	
	@Check("admin")
	public static void remove3(@Required Long moduleId, @Required Long versionId) throws IOException{
		ModuleVersion moduleVersion = getModuleVersion(versionId);
		
		String path = moduleVersion.getPath();
		File repoDir = Util.getRepoDir();
		File moduleDir = new File(repoDir, path);
		FileUtils.deleteDirectory(moduleDir);
		
		moduleVersion.delete();
		
		Repo.index();
	}

}