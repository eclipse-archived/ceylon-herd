package util;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang.StringUtils;

import models.ModuleVersion;
import models.Upload;
import play.Logger;
import play.exceptions.NoRouteFoundException;
import play.i18n.Lang;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Router;
import play.mvc.Router.ActionDefinition;
import play.templates.BaseTemplate;
import play.templates.BaseTemplate.RawData;
import play.utils.HTML;

import com.github.rjeschke.txtmark.BlockEmitter;
import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;
import com.github.rjeschke.txtmark.SpanEmitter;

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
	    return md(mdString, null);
	}
	
	public static Object md(String mdString, ModuleVersion module) {
	    String escaped = HTML.htmlEscape(mdString);
	    
	    Configuration config = Configuration.builder()
	            .forceExtentedProfile()
	            .setCodeBlockEmitter(MarkdownBlockEmitter.INSTANCE)
	            .setSpecialLinkEmitter(module == null ? MarkdownSpanEmitter.INSTANCE : new MarkdownSpanEmitter(module))
	            .build();
	    String html = Processor.process(escaped, config);        

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
    
    private static class MarkdownSpanEmitter implements SpanEmitter {

        private static final MarkdownSpanEmitter INSTANCE = new MarkdownSpanEmitter(null);
        
        private static final String DOT_SEPARATOR = ".";
        private static final String PIPE_SEPARATOR = "|";
        private static final String PATH_SEPARATOR = "/";
        private static final String PACKAGE_SEPARATOR = "::";
        private static final String MODULE_DOC = "module-doc";
        
        private final ModuleVersion currentModule;
        
        public MarkdownSpanEmitter(ModuleVersion currentModule) {
            this.currentModule = currentModule;
        }

        @Override
        public void emitSpan(StringBuilder out, String content) {
            if (currentModule == null || StringUtils.isEmpty(content)) {
                printUnresolvableLink(out, content);
                return;
            }        
            
            String namePart = resolveNamePart(content);
            String declPart = resolveDeclPart(content);
            String declName = resolveDeclName(declPart);
            String packageName = resolvePackageName(declPart);
            
            ModuleVersion module = resolveModule(packageName);
            String moduleDocUrl = resolveModuleDocUrl(module);
            if (moduleDocUrl == null) {
                printUnresolvableLink(out, content);
                return;
            }
            
            String packagePath = resolvePackagePath(module, packageName);
            
            String[] tuple = resolveFileNameAndAnchor(declName);
            String fileName = tuple[0];
            String anchor = tuple[1];
            
            if( docFileExists(module, packagePath, fileName) ) {
                printLink(out, namePart, moduleDocUrl, packagePath, fileName, anchor);
            }
            else {
                printUnresolvableLink(out, content);
            }
        }

        private void printUnresolvableLink(StringBuilder out, String content) {
            out.append("[[").append(content).append("]]");
        }

        private void printLink(StringBuilder out, String namePart, String moduleDocUrl, String packagePath, String fileName, String anchor) {
            out.append("<a href='");
            out.append(moduleDocUrl);
            if (!packagePath.isEmpty()) {
                out.append(packagePath);
                out.append(PATH_SEPARATOR);
            }
            out.append(fileName);
            if (anchor != null) {
                out.append("#");
                out.append(anchor);
            }
            out.append("'>");
            out.append(namePart);
            out.append("</a>");
        }

        private String resolveNamePart(String content) {
            int pipeSeparatorIndex = content.indexOf(PIPE_SEPARATOR);
            if (pipeSeparatorIndex != -1) {
                return content.substring(0, pipeSeparatorIndex);
            } else {
                return content;
            }
        }

        private String resolveDeclPart(String content) {
            int pipeSeparatorIndex = content.indexOf(PIPE_SEPARATOR);
            if (pipeSeparatorIndex != -1) {
                return content.substring(pipeSeparatorIndex + 1, content.length());
            } else {
                return content;
            }
        }

        private String resolveDeclName(String declPart) {
            int packageSeparatorIndex = declPart.indexOf(PACKAGE_SEPARATOR);
            if (packageSeparatorIndex != -1) {
                return declPart.substring(packageSeparatorIndex + 2, declPart.length());
            } else {
                return declPart;
            }
        }

        private String resolvePackageName(String declPart) {
            int packageSeparatorIndex = declPart.indexOf(PACKAGE_SEPARATOR);
            if (packageSeparatorIndex != -1) {
                return declPart.substring(0, packageSeparatorIndex);
            } else {
                return currentModule.module.name; // default package is module root
            }
        }

        private String resolvePackagePath(ModuleVersion moduleVersion, String packageName) {
            String packagePath = packageName.substring(moduleVersion.module.name.length());
            if (packagePath.startsWith(DOT_SEPARATOR)) {
                packagePath = packagePath.substring(1);
            }
            return packagePath;
        }

        private ModuleVersion resolveModule(String packageName) {
            if (packageName.startsWith(currentModule.module.name)) {
                return currentModule;
            } else {
                List<ModuleVersion> dependencies = currentModule.getDependentModuleVersions(); // TODO optimize
                for (ModuleVersion dependency : dependencies) {
                    if (packageName.startsWith(dependency.module.name)) {
                        return dependency;
                    }
                }
                return null;
            }
        }        

        private String resolveModuleDocUrl(ModuleVersion moduleVersion) {
            if( moduleVersion != null ) {
                StringBuilder path = new StringBuilder();
                path.append(moduleVersion.module.name.replace(DOT_SEPARATOR, PATH_SEPARATOR));
                path.append(PATH_SEPARATOR);
                path.append(moduleVersion.version);
                path.append(PATH_SEPARATOR);
                path.append(MODULE_DOC);
                path.append(PATH_SEPARATOR);

                try {
                    return Router.reverse("Repo.viewFile", Collections.singletonMap("path", (Object) path.toString())).url;
                } catch (NoRouteFoundException e) {
                    // noop
                }
            }
            return null;
        }

        private String[] resolveFileNameAndAnchor(String declName) {
            String fileName = null;
            String anchor = null;
        
            String[] names = declName.split("\\" + DOT_SEPARATOR);
            String lastName = names[names.length - 1];
            String prevLastName = names.length > 1 ? names[names.length - 2] : null;
            String rest = names.length > 1 ? declName.substring(0, declName.lastIndexOf(DOT_SEPARATOR)) + "." : "";
        
            if (Character.isUpperCase(lastName.charAt(0))) {
                fileName = rest + lastName + ".type.html";
            }
            else if (prevLastName != null && Character.isUpperCase(prevLastName.charAt(0))) {
                fileName = rest + "type.html";
                anchor = lastName;
            }
            else if (prevLastName != null && Character.isLowerCase(prevLastName.charAt(0))) {
                fileName = rest + "object.html";
                anchor = lastName;
            }
            else {
                fileName = "index.html";
                anchor = lastName;
            }
        
            return new String[] { fileName, anchor };
        }

        private boolean docFileExists(ModuleVersion moduleVersion, String packagePath, String fileName) {
            File repoDir = Util.getRepoDir();
            File moduleDir = new File(repoDir, moduleVersion.module.name.replace(DOT_SEPARATOR, PATH_SEPARATOR) + PATH_SEPARATOR + moduleVersion.version + PATH_SEPARATOR + MODULE_DOC);
            File packageDir = new File(moduleDir, packagePath);
            File f = new File(packageDir, fileName);
            if (f.exists() && f.isFile()) {
                return true;
            }
            return false;
        }

    }

}