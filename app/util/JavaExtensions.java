package util;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import models.Upload;
import play.Logger;
import play.i18n.Lang;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Router.ActionDefinition;
import play.templates.BaseTemplate;
import play.templates.BaseTemplate.RawData;
import play.utils.HTML;

import com.github.rjeschke.txtmark.BlockEmitter;
import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;

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
	    String escaped = HTML.htmlEscape(mdString);

	    Configuration config = Configuration.builder()
	            .forceExtentedProfile()
	            .setCodeBlockEmitter(MarkdownBlockEmitter.INSTANCE)
	            // .setSpecialLinkEmitter(...) TODO
	            .build();
	    String html = Processor.process(escaped, config);        

	    // workaround https://github.com/ceylon/ceylon-herd/issues/74
	    html = html.replaceAll("&amp;((\\w+)|(x?[0-9a-fA-F]+));", "&$1;");
	    
	    return new BaseTemplate.RawData(html);
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
	
    public static RawData xmlEscape(String str) {
        // quick check
        char[] chars = str.toCharArray();
        boolean needsEncoding = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if(Character.isHighSurrogate(c)){
                // skip the next one
                i++;
                continue;
            }
            // gt, lt, quot, amp, apos
            if(c == '>'
                    || c == '<'
                    || c == '"'
                    || c == '&'
                    || c == '\''){
                needsEncoding = true;
                break;
            }
        }
        if(!needsEncoding)
            return new RawData(str);
        // now use code points to be safe
        StringBuilder b = new StringBuilder(str.length());
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if(Character.isHighSurrogate(c)){
                char low = chars[++i];
                int codePoint = Character.toCodePoint(c, low);
                if((0x1 <= codePoint && codePoint <= 0xD7FF) 
                        ||  (0xE000 <= codePoint && codePoint <= 0xFFFD)
                        ||  (0x10000 <= codePoint && codePoint <= 0x10FFFF)){
                    // print as-is
                    b.append(c);
                    b.append(low);
                }else{
                    // needs escaping
                    b.append("&#x").append(String.format("%x", codePoint)).append(";");
                }
                continue;
            }
            if(c == '>')
                b.append("&gt;");
            else if(c == '<')
                b.append("&lt;");
            else if(c == '"')
                b.append("&quot;");
            else if (c == '&')
                b.append("&amp;");
            else if(c == '\'')
                b.append("&apos;");
            // good range (forgetting the higher one as that doesn't fit in a char)
            else if((0x1 <= c && c <= 0xD7FF) ||  (0xE000 <= c && c <= 0xFFFD))
                b.append(c);
            else // needs escaping
                b.append("&#x").append(String.format("%x", (int)c)).append(";");
        }
        return new RawData(b.toString());
    }

    public static RawData jsonEscape(String str) {
        // quick check
        char[] chars = str.toCharArray();
        boolean needsEncoding = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if(Character.isHighSurrogate(c)){
                // skip the next one
                i++;
                continue;
            }
            // every char allowed except " / and control chars 
            // \" \\ \/ \b \f \n \r \t
            if(c == '"'
                    || c == '\\'
                    || c == '\b'
                    || c == '\f'
                    || c == '\n'
                    || c == '\r'
                    || c == '\t'
                    || c <= 0x1f){
                needsEncoding = true;
                break;
            }
        }
        if(!needsEncoding)
            return new RawData(str);
        // now use code points to be safe
        StringBuilder b = new StringBuilder(str.length());
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if(Character.isHighSurrogate(c)){
                // skip the whole pair
                b.append(c);
                b.append(chars[++i]);
                continue;
            }
            if(c == '"')
                b.append("\\\"");
            else if(c == '\\')
                b.append("\\\\");
            else if(c == '\b')
                b.append("\\b");
            else if(c == '\f')
                b.append("\\f");
            else if(c == '\n')
                b.append("\\n");
            else if(c == '\r')
                b.append("\\r");
            else if(c == '\t')
                b.append("\\t");
            else if(c <= 0x1f)
                b.append("\\u").append(String.format("%04x", (int)c));
            else
                b.append(c);
        }
        return new RawData(b.toString());
    }
    
    private static class MarkdownBlockEmitter implements BlockEmitter {

        private static final MarkdownBlockEmitter INSTANCE = new MarkdownBlockEmitter();

        @Override
        public void emitBlock(StringBuilder out, List<String> lines, String meta) {
            if (!lines.isEmpty()) {
                if (meta == null || meta.length() == 0) {
                    out.append("<pre>");
                }
                else {
                    out.append("<pre class=\"brush: ").append(meta).append("\">");
                }
                for (String line : lines) {
                    out.append(line).append('\n');
                }
                out.append("</pre>\n");
            }
        }

    }

}