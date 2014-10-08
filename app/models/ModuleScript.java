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
    
    public ModuleScript(ModuleVersion moduleVersion, String name, String description, boolean unix) {
        this.moduleVersion = moduleVersion;
        this.name = name;
        this.description = description;
        this.unix = unix;
    }

    @Override
    public int compareTo(ModuleScript other) {
        int pkg = name.compareTo(other.name);
        if(pkg != 0)
            return pkg;
        if(unix == other.unix)
            return 0;
        if(unix)
            return -1;
        return 1;
    }

}
