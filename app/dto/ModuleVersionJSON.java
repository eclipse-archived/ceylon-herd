package dto;

import models.ModuleVersion;

public class ModuleVersionJSON {

    private String version;
    private String[] by;
    private String doc;
    private String license;

    public ModuleVersionJSON(ModuleVersion moduleVersion) {
        this.version = moduleVersion.version;
        this.doc = moduleVersion.doc;
        if(!moduleVersion.authors.isEmpty()){
            this.by = new String[moduleVersion.authors.size()];
            for(int i=0;i<this.by.length;i++)
                this.by[i] = moduleVersion.authors.get(i).name;
        }
        this.license = moduleVersion.license;
    }
    
}
