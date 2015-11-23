package controllers;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import com.google.gson.Gson;

import models.Category;
import models.Module;
import models.ModuleVersion;
import models.User;
import play.Logger;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.MimeTypes;
import play.mvc.Before;
import play.mvc.Router;
import util.JavaExtensions;
import util.Util;

public class Repo extends MyController {

	// we set it if it's there, otherwise we don't require it
	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            renderArgs.put("user", user);
        }
    }

	public static void categories() {
		List<Category> categories = Category.findAllCategories();
		render(categories);
	}
	
    public static void category(String categoryName) {
        notFoundIfNull(categoryName);
        Category category = Category.findByName(categoryName);
        notFoundIfNull(category);

        List<Module> modules = Module.findByCategoryFetchOwnerAndVersions(category);
        render(category, modules);
    }

    public static void index(Integer page) {
        if(page == null)
            page = 1;
        List<Module> modules = Module.findAllFetchOwnerAndVersions(page);
        long total = Module.count();
        int pages = Util.pageCount(total);
        render(modules, page, total, pages);
    }

    public static void popular() {
        List<Module> modules = Module.findMostPopular();
        render(modules);
    }

    public static void downloaded(Integer page) {
        if(page == null)
            page = 1;
        List<Module> modules = Module.findMostDownloaded(page);
        long total = Module.count();
        int pages = Util.pageCount(total);
        render(modules, page, total, pages);
    }

	public static void versions(@Required String moduleName){
		if(validationFailed()){
			index(null);
		}
		models.Module module = models.Module.findByName(moduleName);
		if(module == null){
			Validation.addError(null, "Unknown module: "+moduleName);
			prepareForErrorRedirect();
			index(null);
		}
		List<models.ModuleVersion> versions = models.ModuleVersion.findByModule(module);
		Collections.sort(versions);
		Collections.reverse(versions);
		
		render(module, versions);
	}

    public static void search(String q, Integer page) {
        if (isEmpty(q)) {
            index(null);
        }
        if(page == null)
            page = 1;
        List<Module> modules = Module.searchByName(q, page);
        if(modules.size() == 1)
            versions(modules.get(0).name);
        else{
            long total = Module.searchByNameCount(q);
            int pages = Util.pageCount(total);
            String queryPart = Util.unpageQuery(page);
            render(modules, q, page, pages, total, queryPart);
        }
    }
	
    public static void searchAdvanced() {
        List<String> categories = Category.findAllCategoriesNames();
        String categoriesJson = new Gson().toJson(categories);
        render(categoriesJson);
    }
    
    public static void searchAdvanced2(String name, String friendlyName, String member, String license, String category, Integer page) {
        name = trimToNull(name);
        friendlyName = trimToNull(friendlyName);
        license = trimToNull(license);
        category = trimToNull(category);
        member = trimToNull(member);
        if(page == null)
            page = 1;
        
        if (isEmpty(name) && isEmpty(friendlyName) && isEmpty(license) && isEmpty(category) && isEmpty(member)) {
            flash("message", "No search criteria was set.");
            searchAdvanced();
        }
        
        List<Module> modules = Module.searchByCriteria(name, friendlyName, member, license, category, page);
        long total = Module.countByCriteria(name, friendlyName, member, license, category);
        int pages = Util.pageCount(total);
        String queryPart = Util.unpageQuery(page);
        renderTemplate("Repo/search.html", name, friendlyName, member, license, category, modules, page, pages, total, queryPart);
    }

	public static void view(@Required String moduleName, @Required String version){
		models.ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
		models.Module module = moduleVersion.module;
		
		render(module, moduleVersion);
	}

	public static void members(@Required String moduleName, @Required String version){
	    models.ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
	    models.Module module = moduleVersion.module;
	    
	    render(module, moduleVersion);
	}

	public static void scripts(@Required String moduleName, @Required String version){
	    models.ModuleVersion moduleVersion = getModuleVersion(moduleName, version);
	    models.Module module = moduleVersion.module;

	    render(module, moduleVersion);
	}

    public static void importers(@Required String moduleName, String version){
        models.Module module;
        models.ModuleVersion moduleVersion;
        if (version != null && !version.isEmpty()) {
            moduleVersion = getModuleVersion(moduleName, version);
            module = moduleVersion.module;
        } else {
            module = models.Module.findByName(moduleName);
            moduleVersion = null;
        }
        SortedMap<String, SortedSet<ModuleVersion>> dependantsMap = ModuleVersion.findDependants(moduleName, version);
        render(module, moduleVersion, dependantsMap);
    }

	public static void viewDoc(@Required String moduleName, @Required String version){
	    models.ModuleVersion moduleVersion = getModuleVersion(moduleName, version);

	    redirect(Util.viewRepoUrl(moduleVersion.getAPIPath()));
	}

	private static ModuleVersion getModuleVersion(String moduleName, String version) {
		if(validationFailed())
			index(null);
		models.ModuleVersion moduleVersion = models.ModuleVersion.findByVersion(moduleName, version);
		if(moduleVersion == null){
			Validation.addError(null, "Unknown module");
			prepareForErrorRedirect();
			index(null);
		}
		return moduleVersion;
	}

	public static void viewFile(String path) throws IOException{
		File repoDir = Util.getRepoDir();
		File file = new File(repoDir, path);
		checkPath(file, repoDir);
		
		if(!file.exists()){
		    String newDocPath = "/module-doc/api/";
            String oldDocPath = "/module-doc/";
		    if(path.contains(newDocPath)){
		        // try with old path?
		        retryWithSubstitution(repoDir, path, newDocPath, oldDocPath);
		    }else if(path.contains(oldDocPath)){
                // try with new path?
                retryWithSubstitution(repoDir, path, oldDocPath, newDocPath);
            }
			notFound(path);
		}
		if(file.isDirectory()){
            if(!Util.isOnUiHost()){
                redirect(Util.viewRepoUrl(path, true), true);
            }
		    // try a module version
		    ModuleVersion moduleVersion = findModuleVersion(file);
		    Module module = null;
		    if(moduleVersion == null){
		        // try a module
		        module= findModule(file);
		    }
			render("Repo/listFolder.html", file, moduleVersion, module);
		}else{
            if(!Util.isOnDataHost()){
	            notFound();
	        }
		    response.contentType = MimeTypes.getContentType(file.getName());
		    increaseStats(file);
			renderBinary(file);
		}
	}
	
	private static void retryWithSubstitution(File repoDir, String path, String newDocPath, String oldDocPath) throws IOException {
        int start = path.indexOf(newDocPath);
        path = path.substring(0, start) + oldDocPath + path.substring(start+newDocPath.length());
        File file = new File(repoDir, path);
        checkPath(file, repoDir);
        if(file.exists()){
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("path", "");
            String url = Router.reverse("Repo.viewFile", args).toString() + path;
            redirect(url, true);
        }
    }

    private static Module findModule(File file) {
	    for(File f : file.listFiles()){
	        if(!f.isDirectory()){
	            // fail fast: if we have a file we're not in a module dir
	            return null;
	        }
	        // look for a ModuleVersion in the first folder we find
	        ModuleVersion v = findModuleVersion(f);
	        // fail fast if there's no ModuleVersion there, it means we're not in a module dir
	        return v != null ? v.module : null;
	    }
	    return null;
	}

	private static ModuleVersion findModuleVersion(File file) {
	    for(File f : file.listFiles()){
	        if(f.isDirectory()){
	            // fail fast: if we have a directory and it's not "module-doc"
	            // or "module-resources" we're not in a module version dir
	            if(!f.getName().equals("module-doc") && !f.getName().equals("module-resources"))
	                return null;
	            continue;
	        }
	        ModuleVersion v = findModuleVersion(f.getName(), f, ".car");
	        if(v != null)
	            return v;
	    }
	    return null;
    }

    private static void increaseStats(File file) {
	    String name = file.getName();
	    ModuleVersion mv = findModuleVersion(name, file, ".car");
	    if(mv != null){
	        ModuleVersion.incrementDownloads(mv);
	        return;
	    }
        mv = findModuleVersion(name, file, ".jar");
        if(mv != null){
            ModuleVersion.incrementDownloads(mv);
            return;
        }
        mv = findModuleVersion(name, file, ".js");
        if(mv != null){
            ModuleVersion.incrementJSDownloads(mv);
            return;
        }
	    mv = findModuleVersion(name, file, ".src");
        if(mv != null){
            ModuleVersion.incrementSourceDownloads(mv);
            return;
        }
	}
	private static ModuleVersion findModuleVersion(String name, File file, String extension){
	    if(!name.endsWith(extension)){
	        return null;
	    }
	    String path = JavaExtensions.relative(file);
	    Logger.debug("Path: %s", path);
	    int lastFileSep = path.lastIndexOf(File.separatorChar);
	    if(lastFileSep == -1){
	        Logger.info("Got a %s without a module? %s", extension, path);
	        return null;
	    }
	    String moduleAndVersion = path.substring(0, lastFileSep);
	    int versionSep = moduleAndVersion.lastIndexOf(File.separatorChar);
	    if(versionSep == -1){
	        Logger.info("Got a %s without a version? %s", extension, path);
	        return null;
	    }
	    String moduleName = moduleAndVersion.substring(0, versionSep).replace(File.separatorChar, '.');
	    String version = moduleAndVersion.substring(versionSep+1);
	    if(moduleName.isEmpty() || version.isEmpty()){
	        Logger.info("Got a %s with empty name or version? %s", extension, path);
	        return null;
	    }
	    String expectedName = moduleName+"-"+version+extension;
	    if(!name.equals(expectedName)){
	        Logger.info("%s name %s doesn't match expected name %s", extension, name, expectedName);
	        return null;
	    }
	    Logger.debug("We got a %s", extension);
	    ModuleVersion moduleVersion = ModuleVersion.findByVersion(moduleName, version);
	    if(moduleVersion == null){
	        Logger.info("Failed to find a ModuleVersion for %s/%s", moduleName, version);
	        return null;
	    }
	    return moduleVersion;
    }

    public static void noFile() throws IOException{
	    render();
	}

	private static void checkPath(File file, File repoDir) throws IOException{
		String repoPath = repoDir.getCanonicalPath();
		String filePath = file.getCanonicalPath();
		if(!filePath.startsWith(repoPath))
			forbidden("Path is not valid");
	}
}