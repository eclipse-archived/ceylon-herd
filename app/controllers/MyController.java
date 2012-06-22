package controllers;

import java.net.HttpURLConnection;

import models.User;
import play.Logger;
import play.data.validation.Validation;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.results.Error;
import play.mvc.results.Status;

public class MyController extends Controller {
	
    @Before
    static void before(){
        Logger.info("[%s] %s %s", Security.connected(), request.method, request.path);
    }
	
    protected static boolean validationFailed() {
        if(Validation.hasErrors()) {
            prepareForErrorRedirect();
            return true;
        }
        return false;
	}

    protected static void prepareForErrorRedirect() {
		params.flash(); // add http parameters to the flash scope
		Validation.keep(); // keep the errors for the next request
	}

    protected static User getUser() {
		return (User) renderArgs.get("user");
	}
    
	protected static void created() {
		throw new Status(HttpURLConnection.HTTP_CREATED);
	}

	protected static void noContent() {
		throw new Status(HttpURLConnection.HTTP_NO_CONTENT);
	}

    protected static void badRequest(String error) {
        throw new Error(HttpURLConnection.HTTP_BAD_REQUEST, error);
    }
}
