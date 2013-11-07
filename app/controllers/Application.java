package controllers;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import models.User;
import play.Logger;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Http.Header;
import play.mvc.Router;
import play.mvc.results.Status;
import util.ApiVersion;

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
    public final static String SEARCH_MODULES_REL = "http://modules.ceylon-lang.org/rel/search-modules";
    
    public static void options(String version) {
        String returnedVersion;
        // since the version query param is new in M6, M5 installs will not request it and would barf with API3 so we return API1
        // unless explicitly asked for API3
        if(StringUtils.isEmpty(version) || version.equals(ApiVersion.API1.version))
            returnedVersion = ApiVersion.API1.version;
        else if(version.equals(ApiVersion.API2.version))
            returnedVersion = ApiVersion.API2.version;
        else // all versions >3 will be 3 for now
            returnedVersion = ApiVersion.API3.version;
        response.setHeader("X-Herd-Version", returnedVersion);
        response.setHeader("X-Herd-Current-Version", ApiVersion.API3.version);
        response.setHeader("X-Herd-Supported-Versions", ApiVersion.API1.version 
                + "," + ApiVersion.API2.version
                + "," + ApiVersion.API3.version);
        
        // Publish our API via Link headers
        
        // Note that there's a bug in Play 1.2 where only the first Header value is written to the client, so we
        // don't set more than one Link header but concatenate with commas, like the HTTP spec allows
        
        // Format = Link: </>; rel="http://example.net/foo"
        Map<String,Object> args = new HashMap<String,Object>();
        args.put("apiVersion", returnedVersion);
        String completeURL = Router.getFullUrl("RepoAPI.completeModules", args);
        String listVersionsURL = Router.getFullUrl("RepoAPI.completeVersions", args);
        String searchModulesURL = Router.getFullUrl("RepoAPI.searchModules", args);
        String[] links = new String[]{
                "<" + completeURL + ">; rel=\"" + COMPLETE_MODULES_REL + "\"",
                "<" + listVersionsURL + ">; rel=\"" + COMPLETE_VERSIONS_REL + "\"",
                "<" + searchModulesURL + ">; rel=\"" + SEARCH_MODULES_REL + "\"",
        };
        response.setHeader("Link", StringUtils.join(links, ", "));
        throw new Status(HttpURLConnection.HTTP_OK);
    }
}