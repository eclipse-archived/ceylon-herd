package controllers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.Comment;
import models.Module;
import models.ModuleVersion;
import models.Project;
import models.ProjectStatus;
import models.User;
import notifiers.Emails;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Match;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.URL;
import play.data.validation.Validation;
import util.MyCache;
import util.Util;

public class Projects extends LoggedInController {

    public static void index() {
    	User user = getUser();
    	List<models.Project> projects = user.projects;
        render(projects);
    }

    @Check("admin")
    public static void claims() {
    	List<models.Project> projects = models.Project.findClaims();
        render(projects);
    }

	public static void claimForm(String module) {
		render(module);
	}
	
	public static void claim(@Required @Match(message = "validation.moduleName", value = Util.MODULE_NAME_PATTERN) @MaxSize(Util.VARCHAR_SIZE) String module, 
			@Required @MaxSize(Util.VARCHAR_SIZE) @URL String url, 
			@Required @MaxSize(Util.VARCHAR_SIZE) String license, 
			@Required @MaxSize(Util.VARCHAR_SIZE) String role, 
			@Required @MaxSize(Util.TEXT_SIZE) String motivation, 
			@Required @MaxSize(Util.TEXT_SIZE) String description,
			boolean force) {
		if(validationFailed())
			claimForm(null);
		models.Project projectOwner = models.Project.findOwner(module);
		User user = getUser();
		models.Project userClaim = models.Project.findForOwner(module, user);
		// did we already claim it?
		if(userClaim != null){
		    Validation.addError("module", "You already claimed this project");
		}else if(projectOwner != null && !force){
		    // if not, does it belong to someone?
		    alreadyClaimed(module, url, license, role, motivation, description);
		}
		if(validationFailed())
			claimForm(null);
		
		models.Project project = new models.Project();
		project.moduleName = module;
		project.url = url;
		project.owner = user;
		project.license = license;
		project.role = role;
		project.motivation = motivation;
		project.description = description;
		project.status = ProjectStatus.CLAIMED;
		project.create();
		
		Emails.projectClaimNotification(project, user);

		MyCache.evictProjectsForOwner(user);
		MyCache.evictClaims();
		
		flash("message", "Your project has been claimed, we will examine the claim shortly and let you know by email.");
		view(project.id);
    }

	public static void alreadyClaimed(@Required @MaxSize(Util.VARCHAR_SIZE) String module, 
	        @Required @MaxSize(Util.VARCHAR_SIZE) @URL String url, 
	        @Required @MaxSize(Util.VARCHAR_SIZE) String license, 
	        @Required @MaxSize(Util.VARCHAR_SIZE) String role, 
	        @Required @MaxSize(Util.TEXT_SIZE) String motivation, 
	        @Required @MaxSize(Util.TEXT_SIZE) String description) {
        if(validationFailed())
            claimForm(null);
        models.Project projectOwner = models.Project.findOwner(module);
        if(projectOwner == null)
            claimForm(null);
        User existingOwner = projectOwner.owner;
        
        render(module, url, license, role, motivation, description, existingOwner);
	}

	public static void askForParticipation(@Required @MaxSize(Util.VARCHAR_SIZE) String module){
        if(validationFailed())
            claimForm(null);
        models.Project projectOwner = models.Project.findOwner(module);
        if(projectOwner == null)
            claimForm(null);
        User user = getUser();
        Emails.askForParticipation(projectOwner, user);
        
        User existingOwner = projectOwner.owner;
        render(existingOwner);
	}

	public static void view(Long id){
		models.Project project = getProject(id);
		User user = getUser();
		
		Set<User> otherOwners = Collections.emptySet();
		if(user.isAdmin || project.status == ProjectStatus.CONFIRMED){
			List<models.Project> otherProjects = models.Project.find("moduleName = ? AND status = ? AND owner != ?", 
					project.moduleName, ProjectStatus.CONFIRMED, project.owner).fetch();
			otherOwners = new HashSet<User>();
			for(models.Project otherProject : otherProjects)
				otherOwners.add(otherProject.owner);
		}
		render(project, otherOwners);
	}
	
    public static void edit(Long id) {
        Project project = getProject(id);
        render(project);
    }
	
    public static void edit2(Long id,
            @Required @Match(message = "validation.moduleName", value = Util.MODULE_NAME_PATTERN) String moduleName,
            @Required @MaxSize(Util.VARCHAR_SIZE) @URL String url,
            @Required @MaxSize(Util.VARCHAR_SIZE) String license,
            @Required @MaxSize(Util.VARCHAR_SIZE) String role,
            @Required @MaxSize(Util.TEXT_SIZE) String description,
            @Required @MaxSize(Util.TEXT_SIZE) String motivation) {
        Project project = getProject(id);

        // only admin can edit projects in other than claimed status
        User user = getUser();
        if(!project.canBeEdited()
                && !user.isAdmin){
            Validation.addError(null, "Only claimed projects can be edited");
            prepareForErrorRedirect();
            index();
        }
        
        if (validationFailed()) {
            edit(id);
        }

        project.moduleName = moduleName;
        project.url = url;
        project.license = license;
        project.role = role;
        project.description = description;
        project.motivation = motivation;
        project.save();
        
        Comment comment = new Comment();
        comment.status = ProjectStatus.EDITED;
        comment.owner = user;
        comment.date = Util.currentTimeInUTC();
        comment.project = project;
        comment.create();

        Emails.projectEditedNotification(project, user);

        view(id);
    }

	public static void cannotDelete(Long id){
        models.Project project = getProject(id);

        if(project.status != ProjectStatus.CONFIRMED){
            Validation.addError(null, "Project is not confirmed");
            prepareForErrorRedirect();
            view(id);
        }

        Module module = Module.findByName(project.moduleName);
        if(module == null){
            Validation.addError(null, "You are not the module owner");
            prepareForErrorRedirect();
            view(id);
        }

        render(project, module);
	}
	
	public static void delete(Long id){
		models.Project project = getProject(id);

		Logger.info("status: %s", project.status);
		if(project.status == ProjectStatus.CONFIRMED){
		    Module module = Module.findByName(project.moduleName);
	        Logger.info("module: %s, owner: %s", module);
		    if(module != null){
		        cannotDelete(id);
		    }
		}
		
		MyCache.evictProjectsForOwner(project.owner);
        MyCache.evictClaims();
		project.delete();
		
		flash("message", "Project deleted");
		index();
	}
	
	private static models.Project getProject(Long id) {
		if(id == null){
			Validation.addError(null, "Missing project id");
			prepareForErrorRedirect();
			index();
		}
		models.Project project = models.Project.findById(id);
		if(project == null){
			Validation.addError(null, "Invalid project id");
			prepareForErrorRedirect();
			index();
		}
		User user = getUser();
		// for these things you have to be owner or site admin, not module admin
		if(project.owner != user && !user.isAdmin){
			Validation.addError(null, "You are not authorised to view this project");
			prepareForErrorRedirect();
			index();
		}
		return project;
	}

	public static void deleteComment(Long projectId, Long commentId){
		models.Project project = getProject(projectId);
		models.Comment c = getComment(projectId, commentId);
		
		if(c.status != null){
			Validation.addError(null, "Cannot delete status timelines");
			prepareForErrorRedirect();
			view(project.id);
		}
		
		c.delete();
		
		flash("message", "Comment deleted");
		view(project.id);
	}
	
	private static Comment getComment(Long projectId, Long commentId) {
		if(commentId == null){
			Validation.addError(null, "Missing comment id");
			prepareForErrorRedirect();
			view(projectId);
		}
		Comment c = Comment.findById(commentId);
		if(c == null){
			Validation.addError(null, "Invalid comment id");
			prepareForErrorRedirect();
			view(projectId);
		}
		User user = getUser();
		if(c.owner != user && !user.isAdmin){
			Validation.addError(null, "Comment unauthorised");
			prepareForErrorRedirect();
			view(projectId);
		}
		return c;
	}

	public static void addComment(Long id, String text, String projectAction){
		models.Project project = getProject(id);
		if(StringUtils.isEmpty(text) && StringUtils.isEmpty(projectAction)){
			flash("commentWarning", "Empty comment");
			view(id);
		}
		Validation.maxSize("text", text, Util.TEXT_SIZE);
		if(Validation.hasErrors()){
            prepareForErrorRedirect();
            view(id);
		}
		
		User user = getUser();
		
		Logger.info("action: %s", projectAction);
		if("accept".equals(projectAction)){
			if(!user.isAdmin){
				Validation.addError(null, "Unauthorized");
				prepareForErrorRedirect();
				view(id);
			}
			checkBeforeAccept(project);
			
			newStatus(project, ProjectStatus.CONFIRMED, user);
			flash("commentMessage", "Project confirmed");
		}else if("reject".equals(projectAction)){
			if(!user.isAdmin){
				Validation.addError(null, "Unauthorized");
				prepareForErrorRedirect();
				view(id);
			}
			newStatus(project, ProjectStatus.REJECTED, user);
			flash("commentMessage", "Project rejected");
		}else if("claim".equals(projectAction)){
			newStatus(project, ProjectStatus.CLAIMED, user);
			flash("commentMessage", "Project reclaimed");
		}

		if(!StringUtils.isEmpty(text)){
			Comment comment = new Comment();
			comment.text = text;
			comment.owner = user;
			comment.date = Util.currentTimeInUTC();
			comment.project = project;
			comment.create();

			Emails.commentNotification(comment, user);
			flash("commentMessage2", "Comment added");
		}

		view(id);
	}

	private static void newStatus(models.Project project,
			ProjectStatus status, User user) {
		project.status = status;
		project.save();
		Emails.projectStatusNotification(project, user);

		Comment comment = new Comment();
		comment.status = status;
		comment.owner = user;
		comment.date = Util.currentTimeInUTC();
		comment.project = project;
		comment.create();

        MyCache.evictClaims();
	}

	public static void editComment(Long projectId, Long commentId, String text){
		models.Project project = getProject(projectId);
		models.Comment comment = getComment(projectId, commentId);
		
		if(StringUtils.isEmpty(text)){
			flash("commentWarning", "Empty comment");
			flash("commentId", comment.id);
			view(projectId);
		}
        Validation.maxSize("text", text, Util.TEXT_SIZE);
        if(Validation.hasErrors()){
            prepareForErrorRedirect();
            view(projectId);
        }

		comment.text = text;
		comment.save();

		flash("commentMessage", "Comment edited");
		flash("commentId",comment.id);

		view(projectId);
	}

	public static void transferOwnership(Long projectId, Long userId) {
		models.Project project = getProject(projectId);
		User newOwner = getUser(userId);
		checkForTransfer(project, newOwner);
		
		models.Module module = project.getModule();
		Set<ModuleVersion> publishedModuleVersions = null;
		if(module != null){
			 publishedModuleVersions = module.versions;
		}
		render(project, newOwner, publishedModuleVersions, module);
	}

	private static void checkForTransfer(models.Project project, User user) {
		if(project.status != ProjectStatus.CONFIRMED){
			Validation.addError(null, "Project is not confirmed, it cannot be transfered");
			prepareForErrorRedirect();
			Application.index();
		}
		if(project.owner == user){
			Validation.addError(null, "User is already the project owner");
			prepareForErrorRedirect();
			Application.index();
		}
	}

	public static void transferOwnership2(Long projectId, Long userId) {
		models.Project project = getProject(projectId);
		User newOwner = getUser(userId);
		checkForTransfer(project, newOwner);

		User user = getUser();
		// reject previous owner
		newStatus(project, ProjectStatus.REJECTED, user);

		// find the new project claim, if any
		models.Project newProject = models.Project.findForOwner(project.moduleName, newOwner);
		if(newProject == null){
			// must create a new claim
			newProject = new models.Project();
			newProject.moduleName = project.moduleName;
			newProject.owner = newOwner;
			newProject.license = project.license;
			newProject.description = project.description;
			newProject.motivation = project.motivation;
			newProject.role = project.role;
			newProject.url = project.url;
			newProject.status = ProjectStatus.CLAIMED;
			newProject.create();
			MyCache.evictProjectsForOwner(newOwner);
		}
		// accept new owner
		newStatus(newProject, ProjectStatus.CONFIRMED, user);
		
		// now transfer ownership of Module if any
		models.Module module = project.getModule();
		if(module != null){
			module.owner = newOwner;
			// make sure we remove the new owner from the module admins if any
			module.admins.remove(newOwner);
			module.save();
		}
		
		flash("message", "Project transfered to "+newOwner.userName);
		Projects.index();
	}

	private static User getUser(Long userId) {
		if(userId == null){
			Validation.addError(null, "Missing userId");
			prepareForErrorRedirect();
			Application.index();
		}
		User user = User.findById(userId);
		if(user == null){
			Validation.addError(null, "Unknown user");
			prepareForErrorRedirect();
			Application.index();
		}
		return user;
	}

	@Check("admin")
	public static void accept(Long id){
		models.Project project = getProject(id);
		
		checkBeforeAccept(project);

		newStatus(project, ProjectStatus.CONFIRMED, getUser());
		flash("message", "Project confirmed");
		claims();
	}

	private static void checkBeforeAccept(models.Project project) {
		models.Project currentOwnedProject = models.Project.findOwner(project.moduleName); 
		if(currentOwnedProject != null && currentOwnedProject != project){
			transferOwnership(currentOwnedProject.id, project.owner.id);
		}
	}

	@Check("admin")
	public static void reject(Long id){
		models.Project project = getProject(id);
		newStatus(project, ProjectStatus.REJECTED, getUser());
		
		flash("message", "Project rejected");
		claims();
	}
}