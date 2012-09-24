package controllers;

import java.util.List;

import models.User;
import play.data.validation.Email;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import util.Util;

@Check("admin")
public class AdminUsers extends LoggedInController {

	public static void index(int page, int pageSize) {
    	if (page < 1) {
    		page = 1;
    	}
    	if (pageSize < 1 || pageSize > 50) {
    		pageSize = 20;
    	}
    	List<User> users = User.all().fetch(page, pageSize);
    	long userCount = User.count();
		int pageCount = (int) Math.ceil((float) userCount / pageSize);
		render(users, page, pageCount, pageSize);
	}

    public static void editForm(Long id) {
    	notFoundIfNull(id);
    	User editedUser = User.findById(id);
    	notFoundIfNull(editedUser);
    	render(editedUser);
    }

    public static void edit(@Required Long id, 
	        @Required @MaxSize(Util.VARCHAR_SIZE) @Email String email, 
	        @MaxSize(Util.VARCHAR_SIZE) String firstName, 
	        @MaxSize(Util.VARCHAR_SIZE) String lastName, 
	        @Required Boolean isAdmin) {
    	if(validationFailed()) {
    		editForm(id);
    	}
    	
    	User editedUser = User.findById(id);
    	notFoundIfNull(editedUser);
    	editedUser.email = email;
    	editedUser.firstName = firstName;
    	editedUser.lastName = lastName;
    	editedUser.isAdmin = isAdmin;
    	editedUser.save();
    	
    	flash("message", "The user has been modified.");
    	
    	index(1, 20);
    }
    
}
