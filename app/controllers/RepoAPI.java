package controllers;

import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;

import dto.ModuleVersionJSON;

import models.Module;
import models.Module.Type;
import models.ModuleVersion;

public class RepoAPI extends MyController {
    
    public static final int RESULT_LIMIT = 20;

    public static void completeVersions(String module, String version, String type){
        Module mod = Module.findByName(module);
        if(mod == null)
            notFound("Module not found");
        Type t = getType(type);
        
        List<ModuleVersion> versions = ModuleVersion.completeVersionForModuleAndBackend(mod, version, t);
        
        List<ModuleVersionJSON> versionsJSON = new LinkedList<ModuleVersionJSON>();
        for(ModuleVersion v : versions){
            versionsJSON.add(new ModuleVersionJSON(v));
        }
        
        renderJSON(versionsJSON);
    }

    private static Type getType(String type) {
        if(type == null || type.isEmpty())
            return Type.JVM;
        if(type.equalsIgnoreCase("jvm"))
            return Type.JVM;
        if(type.equalsIgnoreCase("javascript"))
            return Type.JS;
        if(type.equalsIgnoreCase("source"))
            return Type.SRC;
        error(HttpURLConnection.HTTP_BAD_REQUEST, "Unknown type, must be one of: jvm,javascript,source");
        // never reached
        return null;
    }

    public static void completeModule(String module, String type){
        Type t = getType(type);

        List<Module> modules = Module.completeForBackend(module, t);
        
        List<String> modulesJSON = new LinkedList<String>();
        for(Module mod : modules){
            modulesJSON.add(mod.name);
        }
        
        renderJSON(modulesJSON);
    }
}
