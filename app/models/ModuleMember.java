package models;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;
import util.CeylonElementType;

@SuppressWarnings("serial")
@Entity
public class ModuleMember extends Model implements Comparable<ModuleMember> {

    @ManyToOne
    public ModuleVersion moduleVersion;
    
    public String name;
    public String packageName;
    
    @Enumerated(EnumType.STRING)
    public CeylonElementType type;

    public ModuleMember(ModuleVersion moduleVersion, String packageName, String name, CeylonElementType type) {
        this.moduleVersion = moduleVersion;
        this.packageName = packageName;
        this.name = name;
        this.type = type;
    }

    @Override
    public int compareTo(ModuleMember other) {
        int pkg = packageName.compareTo(other.packageName);
        if(pkg != 0)
            return pkg;
        int typea = type.typeWeight();
        int typeb = other.type.typeWeight();
        if(typea != typeb)
            return typea < typeb ? -1 : 1;
        return name.compareTo(other.name);
    }

}
