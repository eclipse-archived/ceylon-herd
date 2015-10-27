package models;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import play.db.jpa.Model;
import util.CeylonElementType;

@SuppressWarnings("serial")
@Entity
public class ModuleMember extends Model implements Comparable<ModuleMember> {

    @ManyToOne
    public ModuleVersion moduleVersion;
    
    public String name;
    public String packageName;
    public boolean shared;
    
    @Enumerated(EnumType.STRING)
    public CeylonElementType type;

    public ModuleMember(ModuleVersion moduleVersion, String packageName, String name, CeylonElementType type, boolean shared) {
        this.moduleVersion = moduleVersion;
        this.packageName = packageName;
        this.name = name;
        this.type = type;
        this.shared = shared;
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

    @Transient
    public String getDocUrl(){
        if(!moduleVersion.isAPIPresent)
            return null;
        String path  = moduleVersion.getAPIRootPath();
        String modulePart = moduleVersion.module.name;
        String packagePart = packageName;
        if(packagePart.startsWith(modulePart+"."))
            packagePart = "/"+packagePart.substring(modulePart.length()+1, packagePart.length()).replace('.', '/');
        else if(packagePart.equals(modulePart))
            packagePart = "";
        else
            return null;
        // FIXME: package path?
        switch(type){
        case AnnotationConstructor:
        case Value:
        case Function:
        case Object:
            return path + packagePart + "/index.html#"+name;
        case Annotation:
        case Class:
        case Interface:
            return path + packagePart + "/"+name+".type.html";
        }
        return null;
    }
}
