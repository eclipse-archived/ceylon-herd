package controllers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import models.Category;
import models.Module;
import models.ModuleComment;
import models.ModuleRating;
import models.ModuleVersion;
import models.User;
import notifiers.Emails;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.URL;
import play.data.validation.Validation;
import util.Util;

public class LoggedInRepo extends LoggedInController {

    private static ModuleVersion getModuleVersion(String moduleName, String version) {
        if (validationFailed())
            Repo.index();
        ModuleVersion moduleVersion = ModuleVersion.findByVersion(moduleName, version);
        if (moduleVersion == null) {
            Validation.addError(null, "Unknown module version");
            prepareForErrorRedirect();
            Repo.index();
        }
        return moduleVersion;
    }
	
    private static ModuleVersion getModuleVersionForEdit(String moduleName, String version) {
        ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
        User user = getUser();
        if (!moduleVersion.module.canEdit(user)) {
            Validation.addError(null, "Unauthorised");
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
	    return module;
	}

	private static Module getModuleForEdit(String moduleName) {
		models.Module module = getModule(moduleName);
		User user = getUser();
		if(!module.canEdit(user)){
			Validation.addError(null, "Unauthorised");
			prepareForErrorRedirect();
			Repo.index();
		}
		return module;
	}

	public static void editForm(@Required String moduleName){
		Module module = getModuleForEdit(moduleName);

		List<Category> categories = Category.find("ORDER BY name").fetch();
		
		render(module, categories);
	}


	public static void edit(@Required String moduleName, 
			Long categoryId,
	        @MaxSize(Util.VARCHAR_SIZE) @URL String url, 
	        @MaxSize(Util.VARCHAR_SIZE) @URL String issues, 
	        @MaxSize(Util.VARCHAR_SIZE) @URL String code, 
	        @MaxSize(Util.VARCHAR_SIZE) String friendlyName){
		Module module = getModuleForEdit(moduleName);
		Category category = null;
		if (categoryId != null) {
			category = Category.findById(categoryId);
			Validation.required("categoryId", category);
		}
        if (!getUser().isAdmin && ObjectUtils.notEqual(categoryId, module.category != null ? module.category.id : null)) {
            Validation.addError("categoryId", "Unauthorized category editing");
        }
		
		if(validationFailed()){
		    editForm(moduleName);
		}
		
		module.codeURL = code;
		module.category = category;
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
		Module module = getModuleForEdit(moduleName);
		
		render(module);
	}

	public static void addAdmin(@Required String moduleName, String userName){
		Module module = getModuleForEdit(moduleName);
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
		Module module = getModuleForEdit(moduleName);
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
		Module module = getModuleForEdit(moduleName);
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
		Module module = getModuleForEdit(moduleName);
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
    public static void removeModule1(@Required String moduleName) {
        Module module = getModule(moduleName);
        SortedMap<String, SortedSet<ModuleVersion>> dependantsMap = ModuleVersion.findDependants(moduleName, null);
        render(module, dependantsMap);
    }

    @Check("admin")
    public static void removeModule2(@Required String moduleName) {
        Module module = getModule(moduleName);
        removeModuleDependantsCheck(moduleName);
        render(module);
    }

    @Check("admin")
    public static void removeModule3(@Required String moduleName) throws IOException {
        Module module = getModule(moduleName);
        removeModuleDependantsCheck(moduleName);

        String path = module.getPath();
        File repoDir = Util.getRepoDir();
        File moduleDir = new File(repoDir, path);
        FileUtils.deleteDirectory(moduleDir);
        
        module.delete();
        
        Repo.index();
    }

    private static void removeModuleDependantsCheck(String moduleName) {
        long dependantsCount = ModuleVersion.findDependantsCount(moduleName);
        if (dependantsCount > 0) {
            flash("warning", "Cannot remove module because it has dependencies");
            Repo.versions(moduleName);
        }
    }

    @Check("admin")
	public static void removeModuleVersion1(@Required String moduleName, @Required String version){
		ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
		Module module = moduleVersion.module;
		List<ModuleVersion> dependentModuleVersions = moduleVersion.getDependentModuleVersions();
		render(module, moduleVersion, dependentModuleVersions);
	}
	
	@Check("admin")
	public static void removeModuleVersion2(@Required String moduleName, @Required String version){
		ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
		Module module = moduleVersion.module;

		if (moduleVersion.getDependentModuleVersionCount() > 0) {
		    flash("warning", "Cannot remove module because it has dependencies");
			Repo.view(moduleName, version);
		}
		
		render(module, moduleVersion);
	}
	
	@Check("admin")
	public static void removeModuleVersion3(@Required String moduleName, @Required String version) throws IOException{
		ModuleVersion moduleVersion = getModuleVersion(moduleName, version);

		if (moduleVersion.getDependentModuleVersionCount() > 0) {
            flash("warning", "Cannot remove module because it has dependencies");
			Repo.view(moduleName, version);
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

	public static void addModuleComment(String moduleName, String text, int rating) {
	    Module module = getModule(moduleName);
	    // any logged in user is allowed to comment

	    if(StringUtils.isEmpty(text)){
	        flash("commentWarning", "Empty comment");
	        Repo.versions(moduleName);
	    }
	    Validation.maxSize("text", text, Util.TEXT_SIZE);
	    Validation.range("rating", rating, 0, 5);
	    if(Validation.hasErrors()){
	        prepareForErrorRedirect();
	        Repo.versions(moduleName);
	    }

	    User user = getUser();

	    ModuleComment comment = new ModuleComment();
	    comment.text = text;
	    comment.owner = user;
	    comment.date = Util.currentTimeInUTC();
	    comment.module = module;
	    comment.create();
	    
        ModuleRating moduleRating = module.getRatingFor(user);
        if (moduleRating == null) {
            moduleRating = new ModuleRating();
            moduleRating.module = module;
            moduleRating.owner = user;
        }
        moduleRating.mark = rating;
        moduleRating.save();
	    
	    flash("commentMessage2", "Comment added");
        Emails.moduleCommentNotification(comment, user);
	    Repo.versions(moduleName);
	}

	public static void editModuleComment(String moduleName, Long commentId, String text) {
	    ModuleComment comment = getCommentForEdit(moduleName, commentId);

	    if(StringUtils.isEmpty(text)){
	        flash("commentWarning", "Empty comment");
	        flash("commentId", comment.id);
	        Repo.versions(moduleName);
	    }
	    Validation.maxSize("text", text, Util.TEXT_SIZE);
	    if(Validation.hasErrors()){
	        prepareForErrorRedirect();
	        Repo.versions(moduleName);
	    }

	    comment.text = text;
	    comment.save();

	    flash("commentMessage", "Comment edited");
	    flash("commentId",comment.id);

	    Repo.versions(moduleName);
	}

	public static void deleteModuleComment(String moduleName, Long commentId) {
	    ModuleComment c = getCommentForEdit(moduleName, commentId);

	    c.delete();

	    flash("commentMessage", "Comment deleted");
	    Repo.versions(moduleName);
	}

	private static ModuleComment getCommentForEdit(String moduleName, Long commentId) {
	    // check the module param first
	    getModule(moduleName);
	    // then the comment
	    if(commentId == null){
	        Validation.addError(null, "Missing comment id");
	        prepareForErrorRedirect();
	        Repo.versions(moduleName);
	    }
	    ModuleComment c = ModuleComment.findById(commentId);
	    if(c == null){
	        Validation.addError(null, "Invalid comment id");
	        prepareForErrorRedirect();
	        Repo.versions(moduleName);
	    }
	    // permission check
	    User user = getUser();
	    if(c.owner != user && !user.isAdmin){
	        Validation.addError(null, "Comment unauthorised");
	        prepareForErrorRedirect();
	        Repo.versions(moduleName);
	    }
	    return c;
	}
	
    public static void editChangelog(@Required String moduleName, @Required String version) {
        ModuleVersion moduleVersion = getModuleVersionForEdit(moduleName, version);
        render(moduleVersion);
    }

    public static void editChangelog2(@Required String moduleName, @Required String version, String changelog) {
        ModuleVersion moduleVersion = getModuleVersionForEdit(moduleName, version);

        Validation.maxSize("changelog", changelog, Util.TEXT_SIZE);
        if (Validation.hasErrors()) {
            prepareForErrorRedirect();
            editChangelog(moduleName, version);
        }

        moduleVersion.changelog = changelog;
        moduleVersion.save();

        Repo.view(moduleName, version);
    }
    
}