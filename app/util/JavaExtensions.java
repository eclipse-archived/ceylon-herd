package util;

import markdown.Markdown;
import models.Upload;
import play.Logger;
import play.i18n.Lang;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Router.ActionDefinition;
import play.templates.BaseTemplate;
import play.utils.HTML;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class JavaExtensions extends play.templates.JavaExtensions {

	public static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd HH:mm:ss.SSS";
	public static final String TIMEZONE_DEFAULT = "UTC";

	public static String relativeTo(File file, Upload upload){
		File uploadsDir = Util.getUploadDir(upload.id);
		return relativeTo(file, uploadsDir);
	}

	public static String relative(File file){
		return relativeTo(file, Util.getRepoDir());
	}

	private static String relativeTo(File file, File uploadsDir) {
		try {
			String uploadsPath = uploadsDir.getCanonicalPath();
			String filePath = file.getCanonicalPath();
			if(!filePath.startsWith(uploadsPath))
				throw new RuntimeException("File is not in uploads dir: "+file.getPath());
			String path = filePath.substring(uploadsPath.length());
			if(path.startsWith("/"))
				path = path.substring(1);
			return path;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static boolean isRoot(File file, Upload upload){
		File uploadsDir = Util.getUploadDir(upload.id);
		return isRoot(file, uploadsDir);
	}

	public static boolean isRoot(File file){
		return isRoot(file, Util.getRepoDir());
	}

	private static boolean isRoot(File file, File uploadsDir) {
		try {
			String uploadsPath = uploadsDir.getCanonicalPath();
			String filePath = file.getCanonicalPath();
			if(!filePath.startsWith(uploadsPath))
				throw new RuntimeException("File is not in uploads dir: "+file.getPath());
			return filePath.equals(uploadsPath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static long folderSize(File file){
		if(file.isDirectory()){
			long size = 0;
			for(File child : file.listFiles())
				size += folderSize(child);
			return size;
		}
		return file.length();
	}

	public static long countFiles(File file){
		if(file.isDirectory()){
			long files = 0;
			for(File child : file.listFiles())
				files += countFiles(child);
			return files;
		}
		return 1;
	}
	
	public static Object md(String mdString) {
		try {
		    String html = Markdown.transformMarkdown(HTML.htmlEscape(mdString));
		    // workaround https://github.com/ceylon/ceylon-herd/issues/74
		    html = html.replaceAll("&amp;((\\w+)|(x?[0-9a-fA-F]+));", "&$1;");
			return new BaseTemplate.RawData(html);
		} catch (ParseException e) {
			return e.toString();
		}
	}
	
	public static String toISO8601(Date date) throws DatatypeConfigurationException{
        final GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        final XMLGregorianCalendar xmlCalendar = DatatypeFactory.newInstance()
                        .newXMLGregorianCalendar(calendar);
        return xmlCalendar.toXMLFormat();
	}

    public static String formatInUserTZ(Date date, String pattern) {
        String tz = JavaExtensions.getUserTimeZone();
        if(!"".equals(tz) ){
            Logger.debug("Using user time zone:" +tz);
            return format(JavaExtensions.changeDateTZ(date, pattern, "UTC", tz), pattern, Lang.get(), tz);
        }
        return format(date, pattern, Lang.get());
    }
    
    public static String insecure(ActionDefinition action){
        return action != null ? action.toString().replace("https://", "http://") : null;
    }

	private static String getUserTimeZone() {
		Cookie tz = Request.current().cookies.get("user_tz");
		if(tz!=null) {
			return tz.value;
		}
		return "";
	}

	public static String sinceInUserTZ(Date date,Boolean stopAtMonth ) {

		return sinceInUserTZ(date,stopAtMonth,DATE_FORMAT_DEFAULT);
	}

	public static String sinceInUserTZ(Date date,Boolean stopAtMonth, String dateFormat ) {
		return sinceInUserTZFromOtherTZ(date,stopAtMonth,dateFormat,TIMEZONE_DEFAULT);
	}

	public static String sinceInUserTZFromOtherTZ(Date dateToChange,Boolean stopAtMonth, String dateFormat, String timeZone) {

		Date date = JavaExtensions.changeDateTZ(dateToChange, dateFormat, timeZone, JavaExtensions.getUserTimeZone());
		return since(date,stopAtMonth);
	}

	public static Date changeDateTZ(Date dateToChange, String dateFormat, String originalTz, String targetTz) {
		DateFormat simpleDateFormatUTC = new SimpleDateFormat(dateFormat);
		simpleDateFormatUTC.setTimeZone(TimeZone.getTimeZone(targetTz));
		Date date;
		try {
			DateFormat simpleDateFormatLocal = new SimpleDateFormat(dateFormat);
			simpleDateFormatLocal.setTimeZone(TimeZone.getTimeZone(originalTz));
			date= simpleDateFormatLocal.parse(simpleDateFormatUTC.format(dateToChange));
		} catch (ParseException e) {
			Logger.error("[INTERNAL: problem during processing date from %s timezone to %s timezone in %s format(%s)]",originalTz,targetTz,dateFormat);
			date = dateToChange;
		}
		return date;
	}
}
