package controllers;

import models.Project;
import models.User;
import play.data.validation.Required;
import play.mvc.Before;

import java.util.List;

public class Users extends MyController {

	// we set it if it's there, otherwise we don't require it
	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            renderArgs.put("user", user);
        }
    }

	public static void view(@Required String username){
		models.User viewedUser = models.User.findRegisteredByUserName(username);
		notFoundIfNull(viewedUser);
		
		List<Project> ownedProjects = viewedUser.getOwnedProjects();
		
		render(viewedUser, ownedProjects);
	}

}