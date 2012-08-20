package controllers;

import java.net.HttpURLConnection;

import org.apache.commons.lang.StringUtils;

import models.User;
import play.Logger;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Http.Header;
import play.mvc.Router;
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
    
    // Our HTTP Link header relations
    public final static String COMPLETE_MODULES_REL = "http://modules.ceylon-lang.org/rel/complete-modules";
    public final static String COMPLETE_VERSIONS_REL = "http://modules.ceylon-lang.org/rel/complete-versions";
    
    public static void options() {
        response.setHeader("X-Herd-Version", "1");
        
        // Publish our API via Link headers
        
        // Note that there's a bug in Play 1.2 where only the first Header value is written to the client, so we
        // don't set more than one Link header but concatenate with commas, like the HTTP spec allows
        
        // Format = Link: </>; rel="http://example.net/foo"
        String completeURL = Router.getFullUrl("RepoAPI.completeModules");
        String listVersionsURL = Router.getFullUrl("RepoAPI.completeVersions");
        String[] links = new String[]{
                "<" + completeURL + ">; rel=\"" + COMPLETE_MODULES_REL + "\"",
                "<" + listVersionsURL + ">; rel=\"" + COMPLETE_VERSIONS_REL + "\"",
        };
        response.setHeader("Link", StringUtils.join(links, ", "));
        
        throw new Status(HttpURLConnection.HTTP_OK);
    }
}