package controllers;

import java.net.HttpURLConnection;

import models.User;
import play.Logger;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.results.Status;

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

    public static void usage() {
        render();
    }

    public static void publish() {
        render();
    }
    
    public static void options() {
        response.setHeader("X-Herd-Version", "1");
        throw new Status(HttpURLConnection.HTTP_OK);
    }
}