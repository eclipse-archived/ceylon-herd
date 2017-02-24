package controllers;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;

import models.Module;
import models.ModuleVersion;
import models.User;
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
            // partial path
            handlePartialPath(path);
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
        Set<String> prefixes = null;
        Module module = null;
        ModuleVersion moduleVersion = null;
        String parentPath;
        boolean isListOfFiles = false;
        if(path.isEmpty()){
            parentPath = null;
            prefixes = ModuleVersion.findGroupIdPrefixes("");
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
                        prefixes = new TreeSet<>();
                        module = versions.first().module;
                        for (ModuleVersion mv : versions) {
                            prefixes.add(mv.version);
                        }
                        prefixes.add("maven-metadata.xml");
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
                                prefixes = new TreeSet<>();
                                isListOfFiles = true;
                                String prefix = moduleVersion.getVirtualArtifactId()+"-"+moduleVersion.version;
                                prefixes.add(prefix+".pom");
                                prefixes.add(prefix+".pom.sha1");
                                prefixes.add(prefix+".jar");
                                prefixes.add(prefix+".jar.sha1");
                                if(moduleVersion.isSourcePresent){
                                    prefixes.add(prefix+"-sources.jar");
                                    prefixes.add(prefix+"-sources.jar.sha1");
                                }
                            }
                        }
                    }
                }
                // did not find anything
                if(prefixes == null)
                    prefixes = ModuleVersion.findGroupIdPrefixes(path.replace('/', '.')+".");
            }else{
                prefixes = new TreeSet<>();
                for (ModuleVersion mv : versions) {
                    prefixes.add(mv.getVirtualArtifactId());
                }
            }
        }
        if(prefixes.isEmpty())
            notFound("No such file or folder: "+path);
        render("Repo/listMavenFolder.html", prefixes, path, parentPath, isListOfFiles, moduleVersion, module);
    }
}