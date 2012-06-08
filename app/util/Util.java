package util;

import play.Logger;
import play.mvc.Http.Request;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Util {
    
    // postgres default limit for varchar
    public static final int VARCHAR_SIZE = 255;
    // artificial limit, since for @Lob String, there's no limit (text in DB)
    public static final int TEXT_SIZE = 8192;
	// Date Format
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    
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
}
