package controllers;

import java.util.List;

import models.User;
import models.UserStatus;
import play.libs.Codec;
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
    
    @Check("admin")
    public static void upgrade(){
    	List<User> users = User.all().fetch();
    	for(User user : users){
    		if(user.status == UserStatus.REGISTERED){
    			user.password = Codec.hexSHA1(user.password);
    			user.save();
    		}
    	}
    	flash("message", "upgrade complete");
    	index();
    }

}