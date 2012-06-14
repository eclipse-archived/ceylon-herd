package controllers;

import models.Project;
import play.data.validation.Email;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.Codec;
import play.mvc.Before;
import util.Util;

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

	public static void editForm(@Required String username){
		models.User user = models.User.findRegisteredByUserName(username);
		notFoundIfNull(user);

		render(user);
	}

	public static void edit(@Required String username,
							@MaxSize(Util.VARCHAR_SIZE) String firstName,
							@MaxSize(Util.VARCHAR_SIZE) String lastName,
							@MaxSize(Util.VARCHAR_SIZE) @Email String email){
		models.User user = models.User.findByUserName(username);
		notFoundIfNull(user);

		if(validationFailed()){
			editForm(username);
		}

		user.firstName = firstName;
		user.lastName = lastName;
		user.email = email;
		user.save();

		Users.view(username);

	}

	public static void passwordForm(@Required String username) {
		models.User user = models.User.findRegisteredByUserName(username);
		notFoundIfNull(user);

		render(user);
	}

	public static void passwordEdit(@Required String username,
									@Required @MaxSize(Util.VARCHAR_SIZE) String oldPassword,
									@Required @MaxSize(Util.VARCHAR_SIZE) String password,
									@Required @MaxSize(Util.VARCHAR_SIZE) String password2) {
		models.User user = models.User.findRegisteredByUserName(username);
		notFoundIfNull(user);

		if(validationFailed()){
			passwordForm(username);
		}


		if(!Codec.hexSHA1(user.salt + oldPassword).equals(user.password)){
			Validation.addError("oldPassword","Wrong Password");
			prepareForErrorRedirect();
			passwordForm(username);
		}

		if(!password.equals(password2)) {
			Validation.addError("password2","Confirmation password doesn't match the new password");
			prepareForErrorRedirect();
			passwordForm(username);
		}

		if(password.equals(oldPassword)){
			Validation.addError("password", "Old and new password are the same!");
			prepareForErrorRedirect();
			passwordForm(username);
		}

		user.password = Codec.hexSHA1(user.salt + password);
		user.save();


		Users.view(username);
	}
}