package controllers;

import models.User;
import play.mvc.Before;
import play.mvc.Controller;

public class Application extends Controller {

	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.find("byUserName", Security.connected()).first();
            renderArgs.put("user", user);
        }
    }
	
    public static void index() {
        render();
    }

    public static void about() {
        render();
    }
}