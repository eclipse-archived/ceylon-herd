package controllers;

import java.util.List;

import models.User;

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
    
}
