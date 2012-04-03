package models;

import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class ModuleVersion extends Model {

	@Column(nullable = false)
	public String version;
	
	@JoinColumn(nullable = false)
	@ManyToOne
	public Module module;
	
	@Column(nullable = false)
	public Date published;

	public boolean isAPIPresent;
	public boolean isSourcePresent;
	
	public long downloads;
	public long sourceDownloads;

	@Transient
	public String getPath(){
		return module.name.replace('.', '/')+"/"+version;
	}

	@Transient
	public String getAPIPath(){
		return getPath() + "/module-doc/index.html";
	}

	@Transient
	public String getCarPath(){
		return getPath() + "/" + module.name + "-" + version + ".car";
	}

	@Transient
	public String getSourcePath(){
		return getPath() + "/" + module.name + "-" + version + ".src";
	}

	//
	// Static helpers
	
	public static ModuleVersion findByVersion(String name, String version) {
		return find("module.name = ? AND version = ?", name, version).first();
	}

    public static List<ModuleVersion> findByModule(Module module) {
        return find("module = ?", module).fetch();
    }
    
    public static void incrementDownloads(ModuleVersion v){
        em().createNativeQuery("UPDATE ModuleVersion set downloads = downloads + 1 WHERE id = ?").setParameter(1, v.id).executeUpdate();
    }

    public static void incrementSourceDownloads(ModuleVersion v){
        em().createNativeQuery("UPDATE ModuleVersion set sourceDownloads = sourceDownloads + 1 WHERE id = ?").setParameter(1, v.id).executeUpdate();
    }
}
