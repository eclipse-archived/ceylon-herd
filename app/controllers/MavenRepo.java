package controllers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;

import models.Module;
import models.ModuleVersion;
import models.User;
import play.Logger;
import play.libs.MimeTypes;
import play.mvc.Before;
import play.mvc.Http;
import play.mvc.Scope;
import play.templates.Template;
import play.templates.TemplateLoader;
import util.Util;

public class MavenRepo extends MyController {

    static final Pattern ARTIFACT_PATTERN = Pattern.compile("(.+)/([^/]+)/([^/]+)/\\2-\\3(-[^.]+)?(\\..+?)(\\.sha1)?");
    static final Pattern MODULE_PATTERN = Pattern.compile("(.+)/([^/]+)");

    // we set it if it's there, otherwise we don't require it
    @Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            renderArgs.put("user", user);
        }
    }

    public static void viewVersions(String path) throws IOException{
        if(!Util.isOnDataHost()){
            notFound();
        }
        Matcher matcher = MODULE_PATTERN.matcher(path);
        if(!matcher.matches())
            notFound("Invalid path");
        String group = matcher.group(1).replace('/', '.');
        String artifact = matcher.group(2);
        String moduleName = group + "." + artifact;

        SortedSet<ModuleVersion> moduleVersions = ModuleVersion.findByMavenCoordinates(group, artifact);
        if(moduleVersions.isEmpty())
            notFound("No such module: "+moduleName);
        request.format = "xml";
        // Play doesn't set the charset for us when rendering a template :(
        response.contentType = "application/"+request.format+"; charset="+response.encoding;
        Module module = moduleVersions.first().module;
        render(group, artifact, module);
    }

    public static void viewFile(String path) throws IOException{
        Matcher matcher = ARTIFACT_PATTERN.matcher(path);
        if(!matcher.matches()){
            if(!Util.isOnUiHost()){
                redirect(Util.viewMavenRepoUrl(path, false), true);
            }
            // partial path
            handlePartialPath(path);
        }
        if(!Util.isOnDataHost()){
            notFound();
        }
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

        ModuleVersion moduleVersion = ModuleVersion.findByMavenCoordinates(group, artifact, version);
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
                Scope.RenderArgs templateBinding = Scope.RenderArgs.current();
                templateBinding.data.put("moduleVersion", moduleVersion);
                Template template = TemplateLoader.load(template("MavenRepo/pom.xml"));
                String contents = template.render(templateBinding.data);
                String sha1 = DigestUtils.shaHex(contents);
                renderText(sha1);
            }
        }

        notFound("Unknown file type");
    }

    private static void handlePartialPath(String path) {
        Map<String, Boolean> prefixes = null;
        Module module = null;
        ModuleVersion moduleVersion = null;
        String parentPath;
        boolean isListOfFiles = false;
        if(path.isEmpty()){
            parentPath = null;
            prefixes = falseMap(ModuleVersion.findGroupIdPrefixes(""));
        }else{
            int lastSlash = path.lastIndexOf('/');
            if(lastSlash != -1){
                parentPath = path.substring(0, lastSlash);
            }else{
                parentPath = "";
            }
            // is it an existing group id?
            String groupId = path.replace('/', '.');
            SortedSet<ModuleVersion> versions = ModuleVersion.findByGroupId(groupId);
            if(versions.isEmpty()){
                // Perhaps it's a groupId/artifactId?
                if(lastSlash != -1){
                    groupId = path.substring(0, lastSlash).replace('/', '.');
                    String artifactId = path.substring(lastSlash+1);
                    versions = ModuleVersion.findByMavenCoordinates(groupId, artifactId);
                    if(!versions.isEmpty()){
                        prefixes = new TreeMap<>();
                        module = versions.first().module;
                        for (ModuleVersion mv : versions) {
                            prefixes.put(mv.version, false);
                        }
                        prefixes.put("maven-metadata.xml", true);
                    }else{
                        // Perhaps it's a groupId/artifactId/version?
                        String groupAndArtifactId = path.substring(0, lastSlash);
                        String version = path.substring(lastSlash+1);
                        lastSlash = groupAndArtifactId.lastIndexOf('/');
                        if(lastSlash != -1){
                            groupId = groupAndArtifactId.substring(0, lastSlash).replace('/', '.');
                            artifactId = groupAndArtifactId.substring(lastSlash+1);
                            moduleVersion = ModuleVersion.findByMavenCoordinates(groupId, artifactId, version);
                            if(moduleVersion != null){
                                // fake the files
                                prefixes = new TreeMap<>();
                                isListOfFiles = true;
                                String prefix = moduleVersion.getVirtualArtifactId()+"-"+moduleVersion.version;
                                prefixes.put(prefix+".pom", true);
                                prefixes.put(prefix+".pom.sha1", true);
                                prefixes.put(prefix+".jar", true);
                                prefixes.put(prefix+".jar.sha1", true);
                                if(moduleVersion.isSourcePresent){
                                    prefixes.put(prefix+"-sources.jar", true);
                                    prefixes.put(prefix+"-sources.jar.sha1", true);
                                }
                            }
                        }
                    }
                }
                // did not find anything
                if(prefixes == null){
                    prefixes = falseMap(ModuleVersion.findGroupIdPrefixes(path.replace('/', '.')+"."));
                }
            }else{
                prefixes = new TreeMap<>();
                for (ModuleVersion mv : versions) {
                    prefixes.put(mv.getVirtualArtifactId(), false);
                }
            }
        }
        if(prefixes.isEmpty())
            notFound("No such file or folder: "+path);
        render("Repo/listMavenFolder.html", prefixes, path, parentPath, isListOfFiles, moduleVersion, module);
    }

    private static Map<String, Boolean> falseMap(SortedSet<String> prefixes) {
        Map<String,Boolean> ret = new TreeMap<>();
        for (String prefix : prefixes) {
            ret.put(prefix, false);
        }
        return ret;
    }
}