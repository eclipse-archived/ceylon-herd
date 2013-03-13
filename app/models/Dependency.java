package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import play.db.jpa.Model;
import util.JDKUtil;

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
    
    @Transient
    public boolean isJdk(){
        return JDKUtil.isJdkModule(name);
    }

    @Transient
    public boolean isExists(){
        return ModuleVersion.count("module.name = ? AND version = ?", name, version) > 0;
    }

    @Transient
    public boolean isOtherVersions(){
        return Module.count("name = ?", name) > 0;
    }
}
