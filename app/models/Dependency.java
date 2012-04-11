package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@SuppressWarnings("serial")
@Entity
public class Dependency extends Model {

    @ManyToOne
    public ModuleVersion moduleVersion;
    
    public String name;
    public String version;
    public boolean export;
    public boolean optional;

    public Dependency(ModuleVersion moduleVersion, String name, String version, boolean optional, boolean export) {
        this.moduleVersion = moduleVersion;
        this.name = name;
        this.version = version;
        this.export = export;
        this.optional = optional;
    }
}
