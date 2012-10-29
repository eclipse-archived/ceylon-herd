package util;

import java.util.HashSet;
import java.util.Set;

import play.vfs.VirtualFile;

public class JDKUtil {
    
    private static final Set<String> jdkModules = new HashSet<String>(); 
    
    static {
        VirtualFile file = VirtualFile.open("conf/jdk-modules.conf");
        if(file == null || !file.exists())
            throw new RuntimeException("Can't read jdk-modules.conf");
        String[] lines = file.contentAsString().split("\n");
        for(String line : lines){
            line = line.trim();
            if(!line.isEmpty())
                jdkModules.add(line);
        }
    }
    
    public static boolean isJdkModule(String module){
        return jdkModules.contains(module);
    }
}
