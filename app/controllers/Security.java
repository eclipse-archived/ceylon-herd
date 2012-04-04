package controllers;

import util.Util;
import models.User;


public class Security extends Secure.Security {
	static boolean authenticate(String username, String password) {
	    if(User.connect(username, password) != null)
	        return true;
	    Util.logSecurityAction("failed login for %s", username);
	    return false;
	}
	static boolean check(String profile) {
	    if("admin".equals(profile)) {
	        return User.find("byUserName", connected()).<User>first().isAdmin;
	    }
	    return false;
	}
	static void onDisconnected() {
	    Application.index();
	}
}