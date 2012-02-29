package controllers;

import java.net.HttpURLConnection;

import models.User;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.results.Status;

public class MyController extends Controller {
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
}
