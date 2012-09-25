package controllers;

import models.User;
import play.data.validation.Email;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.Codec;
import util.Util;

public class LoggedInUsers extends LoggedInController {

	public static void editForm(String username){
 		if(username.isEmpty()) {
			 Validation.addError("", "Unknown user");
			 prepareForErrorRedirect();
			 Application.index();
		 }

		models.User editedUser = models.User.findRegisteredByUserName(username);
		notFoundIfNull(editedUser);
		if(!isAuthorised(editedUser)){
			Validation.addError("", "Unauthorised");
			prepareForErrorRedirect();
			Users.view(username);
		}

		render(editedUser);
	}


	public static void edit(@Required String username,
							@MaxSize(Util.VARCHAR_SIZE) String firstName,
							@MaxSize(Util.VARCHAR_SIZE) String lastName,
							@Required @MaxSize(Util.VARCHAR_SIZE) @Email String email,
							boolean isAdmin){

		if(validationFailed()){
			editForm(username);
		}
		models.User user = models.User.findByUserName(username);
		notFoundIfNull(user);

		if(!isAuthorised(user)){
				Validation.addError("", "Unauthorised");
				prepareForErrorRedirect();
				Users.view(username);
		}

		user.firstName = firstName;
		user.lastName = lastName;
		user.email = email;
		// only support setting admin from admins
		if(getUser().isAdmin)
		    user.isAdmin = isAdmin;
		user.save();

		Users.view(username);

	}

	public static void passwordForm(String username) {

		if(username.isEmpty()) {
			Validation.addError("", "Unknown user");
			prepareForErrorRedirect();
			Application.index();
		}

		models.User editedUser = models.User.findRegisteredByUserName(username);
		notFoundIfNull(editedUser);

		if(!isAuthorised(editedUser)){
			Validation.addError("", "Unauthorised");
			prepareForErrorRedirect();
			Users.view(username);
		}

		render(editedUser);
	}

	public static void passwordEdit(@Required String username,
									@Required @MaxSize(Util.VARCHAR_SIZE) String oldPassword,
									@Required @MaxSize(Util.VARCHAR_SIZE) String password,
									@Required @MaxSize(Util.VARCHAR_SIZE) String password2) {
		if(validationFailed()){
			passwordForm(username);
		}
		models.User user = models.User.findRegisteredByUserName(username);
		notFoundIfNull(user);

		if(!isAuthorised(user)){
			Validation.addError("oldPassword","Unauthorised");
			prepareForErrorRedirect();
			Users.view(username);
		}


		if(!Codec.hexSHA1(user.salt + oldPassword).equals(user.password)){
			Validation.addError("oldPassword", "Wrong Password");
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

	private static boolean isAuthorised(User user){
		return getUser() == user || getUser().isAdmin;
	}
}
