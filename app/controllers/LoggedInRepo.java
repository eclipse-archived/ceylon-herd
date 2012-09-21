package controllers;

import models.Dependency;
import models.Module;
import models.ModuleVersion;
import models.User;
import notifiers.Emails;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.URL;
import play.data.validation.Validation;
import util.MyCache;
import util.Util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LoggedInRepo extends LoggedInController {

	private static ModuleVersion getModuleVersion(String moduleName, String version) {
		if(validationFailed())
			Repo.index();
		models.ModuleVersion moduleVersion = models.ModuleVersion.findByVersion(moduleName, version);
		if(moduleVersion == null){
			Validation.addError(null, "Unknown module version");
			prepareForErrorRedirect();
			Repo.index();
		}
		return moduleVersion;
	}

	private static Module getModule(String moduleName) {
		if(moduleName == null){
			Validation.addError(null, "Module name required");
			prepareForErrorRedirect();
			Repo.index();
		}
		models.Module module = models.Module.findByName(moduleName);
		if(module == null){
			Validation.addError(null, "Unknown module");
			prepareForErrorRedirect();
			Repo.index();
		}
		User user = getUser();
		if(!module.canEdit(user)){
			Validation.addError(null, "Unauthorised");
			prepareForErrorRedirect();
			Repo.index();
		}
		return module;
	}

	public static void editForm(@Required String moduleName){
		Module module = getModule(moduleName);
		
		render(module);
	}


	public static void edit(@Required String moduleName, 
	        @MaxSize(Util.VARCHAR_SIZE) @URL String url, 
	        @MaxSize(Util.VARCHAR_SIZE) @URL String issues, 
	        @MaxSize(Util.VARCHAR_SIZE) @URL String code, 
	        @MaxSize(Util.VARCHAR_SIZE) String friendlyName){
		Module module = getModule(moduleName);
		
		if(validationFailed()){
		    editForm(moduleName);
		}
		
		module.codeURL = code;
		module.homeURL = url;
		module.issueTrackerURL = issues;
		module.friendlyName = friendlyName;
		module.save();
		
		Repo.versions(moduleName);
	}

	public static void userList(String term){
		List<String> userNames = Collections.emptyList();
		if(StringUtils.isEmpty(term))
			renderJSON(userNames);
		
		term = term.toLowerCase();
		userNames = User.find("SELECT userName FROM User WHERE LOCATE(?, LOWER(userName)) <> 0", term).fetch(10);
		
		renderJSON(userNames);
	}
	
	public static void permissionsForm(@Required String moduleName){
		Module module = getModule(moduleName);
		
		render(module);
	}

	public static void addAdmin(@Required String moduleName, String userName){
		Module module = getModule(moduleName);
		User user = getUser(userName);
		if(user == null) // error
			permissionsForm(moduleName);
		
		if(module.admins.contains(user) || module.owner.equals(user)){
			flash("message", "User already admin on this module");
			permissionsForm(moduleName);
		}
		
		module.admins.add(user);
		module.save();
		
		Emails.addAdminNotification(module, user, getUser());
		
		flash("message", "User "+user.userName+" added as admin on this project");
		permissionsForm(moduleName);
	}

	private static User getUser(String userName) {
		if(StringUtils.isEmpty(userName)){
			Validation.addError("userName", "User required");
			prepareForErrorRedirect();
			return null;
		}
		User user = User.findRegisteredByUserName(userName);
		if(user == null){
			Validation.addError("userName", "User unknown");
			prepareForErrorRedirect();
			return null;
		}
		return user;
	}

	public static void removeAdmin(@Required String moduleName, Long userId){
		Module module = getModule(moduleName);
		if(userId == null){
			Validation.addError("userName", "User required");
			prepareForErrorRedirect();
			permissionsForm(moduleName);
		}
		User user = User.findById(userId);
		if(user == null){
			Validation.addError("userName", "User unknown");
			prepareForErrorRedirect();
			permissionsForm(moduleName);
		}
		
		if(!module.admins.contains(user)){
			flash("message", "User not admin on this module");
			permissionsForm(moduleName);
		}
		
		module.admins.remove(user);
		module.save();
		
		flash("message", "User "+user.userName+" is not admin anymore on this project");
		permissionsForm(moduleName);
	}

	public static void transferForm(String moduleName){
		Module module = getModule(moduleName);
		checkModuleOwner(module);

		render(module);
	}
	
	private static void checkModuleOwner(Module module) {
		// restricted
		if(!module.isOwnedBy(getUser())){
			Validation.addError(null, "Unauthorised");
			prepareForErrorRedirect();
			LoggedInRepo.editForm(module.name);
		}
	}

	public static void transfer(String moduleName, String userName){
		Module module = getModule(moduleName);
		checkModuleOwner(module);
		
		User newOwner = getUser(userName);
		if(newOwner == null) // error
			transferForm(moduleName);
		
		if(module.owner.equals(newOwner)){
			flash("message", "User already owns this module");
			transferForm(moduleName);
		}
		
		models.Project project = models.Project.findOwner(module.name);
		Projects.transferOwnership(project.id, newOwner.id);
	}
	
	@Check("admin")
	public static void remove1(@Required String moduleName, @Required String version){
		ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
		Module module = moduleVersion.module;
		
		render(module, moduleVersion);
	}
	
	@Check("admin")
	public static void remove2(@Required String moduleName, @Required String version){
		ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
		Module module = moduleVersion.module;
		
		render(module, moduleVersion);
	}
	
	@Check("admin")
	public static void remove3(@Required String moduleName, @Required String version) throws IOException{
		ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
		
		List<ModuleVersion> dependantModuleVersions = moduleVersion.getDependantModuleVersions();
		if (dependantModuleVersions.size() > 0) {
			prepareForErrorRedirect();
			
			String message = "The following modules depend on this module version: <ul>";
			for (ModuleVersion dependantVersion : dependantModuleVersions) {
				message += "<li>" + dependantVersion.module.name + " - " + dependantVersion.version + "</li>";
			}
			message += "</ul>Delete them before deleting this module version.";

	    	flash("error", message);
			Repo.versions(moduleName);
		}
		
		String path = moduleVersion.getPath();
		File repoDir = Util.getRepoDir();
		File moduleDir = new File(repoDir, path);
		FileUtils.deleteDirectory(moduleDir);
		
		moduleVersion.delete();
		
		Repo.index();
	}

	public static void myModules(@Required String username){
		User user = User.find("byUserName", username).first();
		List<models.Module> modules = models.Module.findByOwner(user);
		render(modules);
	}

}