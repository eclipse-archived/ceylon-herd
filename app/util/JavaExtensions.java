package util;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import markdown.Markdown;
import models.Upload;
import play.templates.BaseTemplate;
import play.utils.HTML;

public class JavaExtensions extends play.templates.JavaExtensions {

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
			return new BaseTemplate.RawData(Markdown.transformMarkdown(HTML.htmlEscape(mdString)));
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
}
