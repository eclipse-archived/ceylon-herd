package util;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import play.Logger;
import play.Play;
import play.libs.IO;
import play.mvc.Http.Request;
import play.mvc.Http;
import play.mvc.Router;

import com.google.gson.Gson;

import models.Project;
import models.Upload;

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

    public static final int PAGE_SIZE = 10;

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
    
    public static String viewUploadUrl(Upload upload, String path){
        return viewUploadUrl(upload, path, false);
    }
    
    public static String viewUploadUrl(Upload upload, String path, boolean forceAbsolute){
        return viewUploadUrl(upload, path, forceAbsolute, false);
    }
    
    public static String viewUploadUrl(Upload upload, String path, boolean forceAbsolute, boolean useRepoHost){
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);
        String prefix = getHostPrefix(file, forceAbsolute, useRepoHost);

        Map<String, Object> args = new HashMap<String,Object>();
        args.put("path", path);
        args.put("id", upload.id);
        String pathPart = 
                Router.reverse("UploadRESTReadWrite.viewFile", args)
                    .toString().replace("%2F", "/");
        return makeUrl(prefix, pathPart);
    }

    public static String viewPublicUploadUrl(Upload upload, String path){
        return viewPublicUploadUrl(upload, path, false);
    }
    
    public static String viewPublicUploadUrl(Upload upload, String path, boolean forceAbsolute){
        return viewPublicUploadUrl(upload, path, forceAbsolute, false);
    }
    
    public static String viewPublicUploadUrl(Upload upload, String path, boolean forceAbsolute, boolean useRepoHost){
        File uploadsDir = Util.getUploadDir(upload.id);
        File file = new File(uploadsDir, path);
        String prefix = getHostPrefix(file, forceAbsolute, useRepoHost);

        Map<String, Object> args = new HashMap<String,Object>();
        args.put("path", path);
        args.put("id", upload.id);
        String pathPart = 
                Router.reverse("UploadRESTReadOnly.viewFile", args)
                    .toString().replace("%2F", "/");
        return makeUrl(prefix, pathPart);
    }

    public static String viewRepoUrl(String path){
        return viewRepoUrl(path, false);
    }
    
    public static String viewRepoUrl(String path, boolean forceAbsolute){
        return viewRepoUrl(path, forceAbsolute, false);
    }
    
    public static String viewRepoUrl(String path, boolean forceAbsolute, boolean useRepoHost){
        File repoDir = Util.getRepoDir();
        File file = new File(repoDir, path);
        String prefix = getHostPrefix(file, forceAbsolute, useRepoHost);
            
        Map<String, Object> args = new HashMap<String,Object>();
        args.put("path", path);
        String pathPart = Router.reverse("Repo.viewFile", args).toString().replace("%2F", "/");
        return makeUrl(prefix, pathPart);
    }

    private static String makeUrl(String prefix, String pathPart) {
        if(prefix.isEmpty())
            return pathPart;
        if(pathPart.isEmpty())
            return prefix;
        if(pathPart.startsWith("/"))
            return prefix + pathPart;
        return prefix + "/" + pathPart;
    }

    private static String getHostPrefix(File file, boolean forceAbsolute, boolean useRepoHost) {
        boolean isSecure = Http.Request.current() == null ? false : Http.Request.current().secure;
        String proto = isSecure ? "https://" : "http://";
        if(file.exists()){
            if(!file.isDirectory()){
                return proto + getDataHost();
            }
        }
        if(forceAbsolute){
            if(useRepoHost)
                return proto + getDataHost();
            else
                return proto + getUiHost();
        }
        return "";
    }
    
    public static boolean isSplitHost(){
        String property = Play.configuration.getProperty("splitHost.enabled");
        return property != null 
                && (property.toLowerCase().equals("true")
                        || property.toLowerCase().equals("yes"));
    }
    
    public static String getDataHost(){
        if(isSplitHost()){
            String host = Play.configuration.getProperty("splitHost.dataHost");
            if(host == null)
                throw new RuntimeException("Missing splitHost.dataHost host name when splitHost.enabled is true");
            return host;
        }
        return Http.Request.current().host;
    }

    public static String getUiHost(){
        if(isSplitHost()){
            String host = Play.configuration.getProperty("splitHost.uiHost");
            if(host == null)
                throw new RuntimeException("Missing splitHost.uiHost host name when splitHost.enabled is true");
            return host;
        }
        return Http.Request.current().host;
    }

    public static boolean isOnUiHost() {
        if(!isSplitHost())
            return true;
        return Http.Request.current().host.equals(getUiHost());
    }

    public static boolean isOnDataHost() {
        if(!isSplitHost())
            return true;
        return Http.Request.current().host.equals(getDataHost());
    }

    public static int pageCount(long total) {
        return (int)Math.ceil((double)total / Util.PAGE_SIZE);
    }

    public static <T> List<T> page(List<T> list, int page) {
        List<T> ret = new ArrayList<T>(PAGE_SIZE);
        for(int i=(page-1)*PAGE_SIZE, end = Math.min(list.size(), i+PAGE_SIZE) ; i < end; i++){
            ret.add(list.get(i));
        }
        return ret;
    }

    public static String unpageQuery(Integer page) {
        String query = Http.Request.current().querystring;
        if(page == null)
            return query;
        return query.replace("?page="+page, "?").replace("&page="+page, "");
    }
}
