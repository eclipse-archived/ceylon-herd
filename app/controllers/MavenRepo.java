package controllers;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;

import models.Module;
import models.ModuleVersion;
import models.User;
import play.Logger;
import play.data.validation.Validation;
import play.libs.MimeTypes;
import play.mvc.Before;
import play.mvc.Http;
import play.mvc.Scope;
import play.mvc.results.RenderTemplate;
import play.templates.Template;
import play.templates.TemplateLoader;
import util.Util;

public class MavenRepo extends MyController {

    static final Pattern ARTIFACT_PATTERN = Pattern.compile("(.+)/([^/]+)/([^/]+)/\\2-\\3(-[^.]+)?(\\..+?)(\\.sha1)?");
    static final Pattern MODULE_PATTERN = Pattern.compile("(.+)/([^/]+)");

    // we set it if it's there, otherwise we don't require it
    @Before
    static void setConnectedUser() {
        if(!Util.isOnDataHost()){
            notFound();
        }
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            renderArgs.put("user", user);
        }
    }

    public static void viewVersions(String path) throws IOException{
        Matcher matcher = MODULE_PATTERN.matcher(path);
        if(!matcher.matches())
            notFound("Invalid path");
        String group = matcher.group(1).replace('/', '.');
        String artifact = matcher.group(2);
        String moduleName = group + "." + artifact;

        Module module = Module.findByName(moduleName);
        if(module == null)
            notFound("No such module: "+moduleName);
        request.format = "xml";
        // Play doesn't set the charset for us when rendering a template :(
        response.contentType = "application/"+request.format+"; charset="+response.encoding;
        render(group, artifact, module);
    }

    public static void viewFile(String path) throws IOException{
        Matcher matcher = ARTIFACT_PATTERN.matcher(path);
        if(!matcher.matches())
            notFound("Invalid path");
        String group = matcher.group(1).replace('/', '.');
        String artifact = matcher.group(2);
        String version = matcher.group(3);
        String classifier = matcher.group(4);
        String extension = matcher.group(5);
        String checksum = matcher.group(6);
        if(checksum == null)
            checksum = "";
        if(classifier == null)
            classifier = "";
        boolean wantsSources = false;
        if("-sources".equals(classifier))
            wantsSources = true;
        else if(!classifier.isEmpty())
            notFound("Classifiers not supported yet");

        String module = group + "." + artifact;
        ModuleVersion moduleVersion = ModuleVersion.findByVersion(module, version);
        if(moduleVersion == null)
            notFound("No such version");

        if(!moduleVersion.isJarPresent && !moduleVersion.isCarPresent)
            notFound("Module not available for Jvm");

        if(extension.equals(".jar")){
            File repoDir = Util.getRepoDir();
            if(wantsSources && !moduleVersion.isSourcePresent)
                notFound("Module sources not available");
            
            String wantedPath;
            if(wantsSources)
                wantedPath = moduleVersion.getSourcePath();
            else if(moduleVersion.isJarPresent)
                wantedPath = moduleVersion.getJarPath();
            else
                wantedPath = moduleVersion.getCarPath();
            
            File file = new File(repoDir, wantedPath + checksum);
            Repo.checkPath(file, repoDir);
            
            String jarName = artifact+"-"+version+classifier+extension+checksum;
            if(!file.exists())
                notFound("File missing");
            
            response.contentType = MimeTypes.getContentType(file.getName());
            response.headers.put("Content-Disposition", new Http.Header("Content-Disposition", "inline;filename="+jarName));
            
            if(wantsSources)
                ModuleVersion.incrementSourceDownloads(moduleVersion);
            else if(checksum.isEmpty())
                ModuleVersion.incrementDownloads(moduleVersion);
            
            renderBinary(file);
        }else if(extension.equals(".pom")){
            if(checksum.isEmpty()){
                request.format = "xml";
                // Play doesn't set the charset for us when rendering a template :(
                response.contentType = "application/"+request.format+"; charset="+response.encoding;
                render("MavenRepo/pom.xml", moduleVersion);
            }else{
                Logger.info("sha1");
                Scope.RenderArgs templateBinding = Scope.RenderArgs.current();
                templateBinding.data.put("moduleVersion", moduleVersion);
                Template template = TemplateLoader.load(template("MavenRepo/pom.xml"));
                String contents = template.render(templateBinding.data);
                String sha1 = DigestUtils.shaHex(contents);
                Logger.info("sha1 text");
                renderText(sha1);
            }
        }

        notFound("Unknown file type");
    }

}