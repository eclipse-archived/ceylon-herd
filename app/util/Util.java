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
	
	public static int compareVersions(String versionAString, String versionBString){
        char[] versionA = versionAString.toCharArray();
        char[] versionB = versionBString.toCharArray();
        int aStart = 0, aEnd = 0;
        int bStart = 0, bEnd = 0;
        // we follow the debian algo of sorting first all non-digits, then all digits in turn
        while(true){
            // collect all chars until digits or end
            while(aEnd < versionA.length && !Character.isDigit(versionA[aEnd]))
                aEnd++;
            while(bEnd < versionB.length && !Character.isDigit(versionB[bEnd]))
                bEnd++;
            int compare = compare(versionA, aStart, aEnd, versionB, bStart, bEnd);
            if(compare != 0)
                return compare;
            // if we've exhausted one, it wins
            if(aEnd == versionA.length && bEnd == versionB.length)
                return 0;
            if(aEnd == versionA.length)
                return -1;
            if(bEnd == versionB.length)
                return 1;
            // now collect all digits until non-digit or end
            int a = 0, b = 0;
            while(aEnd < versionA.length && Character.isDigit(versionA[aEnd])){
                a = a * 10 + (versionA[aEnd] - '0');
                aEnd++;
            }
            while(bEnd < versionB.length && Character.isDigit(versionB[bEnd])){
                b = b * 10 + (versionB[bEnd] - '0');
                bEnd++;
            }
            // now compare
            compare = compare(a, b);
            if(compare != 0)
                return compare;
            // if we've exhausted one, it wins
            if(aEnd == versionA.length && bEnd == versionB.length)
                return 0;
            if(aEnd == versionA.length)
                return -1;
            if(bEnd == versionB.length)
                return 1;
            // and on to the next part
        }
    }
    
    private static int compare(int a, int b) {
        // Integer.compare and Character.compare are only on 1.7
        return a < b ? -1 : a == b ? 0 : 1;
    }

    private static int compare(char[] a, int aStart, int aEnd, char[] b, int bStart, int bEnd) {
        for (; aStart < aEnd && bStart < bEnd; aStart++, bStart++) {
            char aChar = a[aStart];
            char bChar = b[bStart];
            if(isAlphabetic(aChar)){
                if(isAlphabetic(bChar)){
                    int ret = compare(aChar, bChar);
                    if(ret != 0)
                        return ret;
                }else{
                    // alphabetic wins
                    return -1;
                }
            }else if(isAlphabetic(bChar)){
                // alphabetic wins
                return 1;
            }else{
                // both non-alphabetic
                int ret = compare(aChar, bChar);
                if(ret != 0)
                    return ret;
            }
            // equal, try the next char
        }
        // shortest one wins
        if(aStart == aEnd && bStart == bEnd)
            return 0;
        if(aStart == aEnd)
            return -1;
        return 1;
    }

    private static boolean isAlphabetic(char c) {
        return c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z';
    }
}
