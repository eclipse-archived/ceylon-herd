package controllers;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import notifiers.Emails;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Validation;

import models.Comment;
import models.ProjectStatus;
import models.User;

public class Project extends LoggedInController {

    public static void index() {
    	User user = getUser();
    	List<models.Project> projects = user.projects;
        render(projects);
    }

    @Check("admin")
    public static void claims() {
    	List<models.Project> projects = models.Project.find("status = ?", ProjectStatus.CLAIMED).fetch();
        render(projects);
    }

	public static void claimForm(String module) {
		render(module);
	}
	
	public static void claim(@Required String module, 
			@Required String url, 
			@Required String license, 
			@Required String role, 
			@Required String motivation, 
			@Required String description) {
		if(validationFailed())
			claimForm(null);
		models.Project project = models.Project.find("moduleName = ?", module).first();
		User user = getUser();
		if(project != null){
			if(project.owner == user)
				Validation.addError("module", "You already claimed this project");
		}
		if(validationFailed())
			claimForm(null);
		
		project = new models.Project();
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

		flash("message", "Your project has been claimed, we will examine the claim shortly and let you know by email.");
		view(project.id);
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

	public static void delete(Long projectId){
		models.Project project = getProject(projectId);
		
		project.delete();
		
		flash("message", "Project deleted");
		index();
	}
	
	private static models.Project getProject(Long id) {
		if(validationFailed())
			index();
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
			flash("warning", "Empty comment");
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
			newStatus(project, ProjectStatus.CONFIRMED, user);
			flash("message2", "Project confirmed");
		}else if("reject".equals(projectAction)){
			if(!user.isAdmin){
				Validation.addError(null, "Unauthorized");
				prepareForErrorRedirect();
				view(id);
			}
			newStatus(project, ProjectStatus.REJECTED, user);
			flash("message2", "Project rejected");
		}else if("claim".equals(projectAction)){
			newStatus(project, ProjectStatus.CLAIMED, user);
			flash("message2", "Project reclaimed");
		}

		if(!StringUtils.isEmpty(text)){
			Comment comment = new Comment();
			comment.text = text;
			comment.owner = user;
			comment.date = new Date();
			comment.project = project;
			comment.create();

			Emails.commentNotification(comment, user);
			flash("message", "Comment added");
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
		comment.date = new Date();
		comment.project = project;
		comment.create();
	}

	public static void editComment(Long projectId, Long commentId, String text){
		models.Project project = getProject(projectId);
		models.Comment comment = getComment(projectId, commentId);
		
		if(StringUtils.isEmpty(text)){
			flash("warning", "Empty comment");
			view(projectId);
		}

		comment.text = text;
		comment.save();

		flash("message", "Comment edited");

		view(projectId);
	}

	@Check("admin")
	public static void accept(Long id){
		models.Project project = getProject(id);
		newStatus(project, ProjectStatus.CONFIRMED, getUser());
		flash("message", "Project confirmed");
		claims();
	}

	@Check("admin")
	public static void reject(Long id){
		models.Project project = getProject(id);
		newStatus(project, ProjectStatus.REJECTED, getUser());
		
		flash("message", "Project rejected");
		claims();
	}
}