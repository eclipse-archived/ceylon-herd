package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import models.Module.Type;
import play.db.jpa.Model;
import util.Util;
import controllers.RepoAPI;

@Entity
@SuppressWarnings("serial")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"module_id", "version"}))
public class ModuleVersion extends Model implements Comparable<ModuleVersion> {

	@Column(nullable = false)
	public String version;
	
	@JoinColumn(nullable = false)
	@ManyToOne
	public Module module;
	
	@Column(nullable = false)
	public Date published;

    public boolean isCarPresent;
    public boolean isJarPresent;
    public boolean isJsPresent;
	public boolean isAPIPresent;
	public boolean isSourcePresent;
    public boolean isRunnabled;
	
	public long downloads;
    public long jsdownloads;
	public long sourceDownloads;

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String doc;
    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String license;
	
    public int ceylonMajor;
    public int ceylonMinor;

	@OrderBy("name,version")
	@OneToMany(mappedBy = "moduleVersion", cascade = CascadeType.REMOVE)
    private List<Dependency> dependencies = new ArrayList<Dependency>();

    @OrderBy("name")
    @ManyToMany
    public List<Author> authors = new ArrayList<Author>();

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
    public String getJarPath(){
        return getPath() + "/" + module.name + "-" + version + ".jar";
    }

    @Transient
    public String getJsPath(){
        return getPath() + "/" + module.name + "-" + version + ".js";
    }

	@Transient
	public String getSourcePath(){
		return getPath() + "/" + module.name + "-" + version + ".src";
	}

    public void addDependency(String name, String version, boolean optional, boolean export) {
        Dependency dep = new Dependency(this, name, version, optional, export);
        dep.create();
        dependencies.add(dep);
    }

    @Override
    public int compareTo(ModuleVersion other) {
        return Util.compareVersions(version, other.version);
    }
    
	//
	// Static helpers
	
	public static ModuleVersion findByVersion(String name, String version) {
		return find("module.name = ? AND version = ?", name, version).first();
	}

    public static List<ModuleVersion> findByModule(Module module) {
        return find("module = ? ORDER BY published DESC", module).fetch();
    }
    
    public static void incrementDownloads(ModuleVersion v){
        em().createNativeQuery("UPDATE ModuleVersion set downloads = downloads + 1 WHERE id = ?").setParameter(1, v.id).executeUpdate();
    }

    public static void incrementJSDownloads(ModuleVersion v){
        em().createNativeQuery("UPDATE ModuleVersion set jsdownloads = jsdownloads + 1 WHERE id = ?").setParameter(1, v.id).executeUpdate();
    }

    public static void incrementSourceDownloads(ModuleVersion v){
        em().createNativeQuery("UPDATE ModuleVersion set sourceDownloads = sourceDownloads + 1 WHERE id = ?").setParameter(1, v.id).executeUpdate();
    }

    public static List<ModuleVersion> latest(int max) {
        return find("ORDER BY published DESC").fetch(max);
    }

    public static List<ModuleVersion> latestForModule(String module, int max) {
        return find("module.name = ? ORDER BY published DESC", module).fetch(max);
    }

    public static List<ModuleVersion> latestForOwner(User owner, int max) {
        return find("module.owner = ? ORDER BY published DESC", owner).fetch(max);
    }

    public static long countForOwner(User owner) {
        return count("module.owner = ?", owner);
    }

    static String getBackendQuery(String prefix, Type t){
        switch(t){
        case JS:
            return prefix+"isJsPresent = true";
        case JVM:
            return prefix+"isCarPresent = true OR "+prefix+"isJarPresent = true";
        case SRC:
            return prefix+"isSourcePresent = true";
        default:
            // ouch
            throw new RuntimeException("Invalid switch statement: missing enum cases");    
        }
    }

    public static List<ModuleVersion> completeVersionForModuleAndBackend(Module module, String version, Type type) {
        String typeQuery = ModuleVersion.getBackendQuery("", type);
        if(version == null)
            version = "";
        return ModuleVersion.find("module = ? AND LOCATE(?, version) = 1 AND ("+typeQuery+")"
                + " ORDER BY version", module, version).fetch(RepoAPI.RESULT_LIMIT);
    }
}
