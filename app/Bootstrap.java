import java.io.File;

import play.*;
import play.jobs.*;
import play.test.*;
 
import models.*;
 
@OnApplicationStart
public class Bootstrap extends Job {
 
    public void doJob() {
       
    	File repo = new File(Play.applicationPath, "repo");
    	if (!repo.exists()) {
    		repo.mkdir();
    	}
    	
    	File uploads = new File(Play.applicationPath, "uploads");
    	if (!uploads.exists()) {
    		uploads.mkdir();
    	}
    	
    	if (Play.mode.isDev()) {
    	    HerdMetainf.initialize();
    	}
    	
    }
 
}