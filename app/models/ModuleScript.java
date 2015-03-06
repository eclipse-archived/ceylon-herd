package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@SuppressWarnings("serial")
@Entity
public class ModuleScript extends Model implements Comparable<ModuleScript> {

    @ManyToOne
    public ModuleVersion moduleVersion;
    
    public String name;
    public String description;
    public boolean unix;
    public boolean plugin;
    public String module;
    
    public ModuleScript(ModuleVersion moduleVersion, String name, String description, boolean unix, boolean plugin, String module) {
        this.moduleVersion = moduleVersion;
        this.name = name;
        this.description = description;
        this.unix = unix;
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public int compareTo(ModuleScript other) {
        int pkg = name.compareTo(other.name);
        if(pkg != 0)
            return pkg;
        if(plugin)
            return 1;
        if(unix == other.unix)
            return 0;
        if(unix)
            return -1;
        return 1;
    }

}
