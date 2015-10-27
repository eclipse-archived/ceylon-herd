package util;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import play.Logger;
import play.Play;
import play.libs.IO;
import play.mvc.Http.Request;
import play.mvc.Router;

import com.google.gson.Gson;

public class Util {

    // Same as GitHub
    public static final int USER_NAME_SIZE = 39;

    // postgres default limit for varchar
    public static final int VARCHAR_SIZE = 255;
    // artificial limit, since for @Lob String, there's no limit (text in DB)
    public static final int TEXT_SIZE = 8192;
	// Date Format
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
	
    public static final String LICENSES_JSON;

    static {
        List<String> licenses = IO.readLines(Play.getFile("conf/licenses.conf"));
        LICENSES_JSON = new Gson().toJson(licenses);
    }

    // Pattern for module names checking
    public static final String MODULE_NAME_PATTERN = "[a-zA-Z][-a-zA-Z0-9]*(\\.[a-zA-Z][-a-zA-Z0-9]*)+";

	public static File getUploadDir(Long id) {
		return new File("uploads"+File.separator+id);
	}

	public static File getRepoDir() {
		return new File("repo");
	}

    public static void logSecurityAction(String message, Object... params) {
        Object[] newParams = new Object[params.length+1];
        System.arraycopy(params, 0, newParams, 1, params.length);
        newParams[0] = Request.current().remoteAddress;
        Logger.info("[SECURITY: %s]: "+message, newParams);
    }

	public static Date currentTimeInUTC() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
		simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = new Date();
		try {
			date= new SimpleDateFormat(DATE_FORMAT).parse(simpleDateFormat.format(date));
		} catch (ParseException e) {
			Logger.error("[INTERNAL: problem during processing current time in UTC format(%s)]",DATE_FORMAT);
		}
		return date;

	}
	
	public static int compareVersions(String versionAString, String versionBString){
	    return MavenVersionComparator.compareVersions(versionAString, versionBString);
    }
    
    public static String viewUploadUrl(Long id, String path){
        Map<String, Object> args = new HashMap<String,Object>();
        args.put("path", path);
        args.put("id", id);
        return Router.reverse("UploadAPI.viewFile", args).toString().replace("%2F", "/");
    }

    public static String viewRepoUrl(String path){
        Map<String, Object> args = new HashMap<String,Object>();
        args.put("path", path);
        return Router.reverse("Repo.viewFile", args).toString().replace("%2F", "/");
    }
}
