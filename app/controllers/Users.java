package controllers;

import models.Project;
import play.data.validation.Required;
import play.mvc.Before;

import java.util.List;

public class Users extends MyController {

	// we set it if it's there, otherwise we don't require it
	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
        	models.User user = models.User.find("byUserName", Security.connected()).first();
            renderArgs.put("user", user);
        }
    }

	public static void view(@Required String username){
		models.User user = models.User.findRegisteredByUserName(username);
		notFoundIfNull(user);
		
		List<Project> ownedProjects = user.getOwnedProjects();
		
		render(user, ownedProjects);
	}

}