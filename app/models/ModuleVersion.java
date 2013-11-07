package models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

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

import play.db.jpa.JPA;
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
    public boolean isDocPresent;
    public boolean isJsPresent;
	public boolean isAPIPresent;
	public boolean isSourcePresent;
    public boolean isRunnable;
	
	public long downloads;
    public long jsdownloads;
	public long sourceDownloads;

    @Column(columnDefinition = "TEXT") // Hibernate would map @Lob to a CLOB instead of TEXT
	public String doc;
    @Column(columnDefinition = "TEXT") // Hibernate would map @Lob to a CLOB instead of TEXT
	public String license;
    @Column(columnDefinition = "TEXT") // Hibernate would map @Lob to a CLOB instead of TEXT
    public String changelog;
	
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

	@Transient
	public String getDocPath(){
	    return getPath() + "/" + module.name + "-" + version + ".doc.zip";
	}

    public void addDependency(String name, String version, boolean optional, boolean export, boolean resolvedFromMaven) {
        Dependency dep = new Dependency(this, name, version, optional, export, resolvedFromMaven);
        dep.create();
        dependencies.add(dep);
    }

    public int getDependentModuleVersionCount() {
    	return JPA.em().createQuery("SELECT count(v) FROM ModuleVersion v JOIN v.dependencies d WHERE d.name=:name and d.version=:version", Long.class)
    			       .setParameter("name", module.name)
    			       .setParameter("version", version).getSingleResult().intValue();
    }    

    public List<ModuleVersion> getDependentModuleVersions() {
    	return JPA.em().createQuery("SELECT v FROM ModuleVersion v JOIN v.dependencies d WHERE d.name=:name and d.version=:version", ModuleVersion.class)
    			       .setParameter("name", module.name)
    			       .setParameter("version", version).getResultList();
    }

    @Override
    public int compareTo(ModuleVersion other) {
        return Util.compareVersions(version, other.version);
    }
    
	//
	// Static helpers
    
    public static SortedMap<String, SortedSet<ModuleVersion>> findDependants(String moduleName) {
        List<Object[]> results = JPA.em()
                .createQuery("SELECT d.version, v FROM ModuleVersion v JOIN v.dependencies d LEFT JOIN FETCH v.module WHERE d.name=:name")
                .setParameter("name", moduleName)
                .getResultList();

        Comparator<String> versionComparator = new Comparator<String>() {
            @Override
            public int compare(String v1, String v2) {
                return Util.compareVersions(v1, v2);
            }
        };

        Comparator<ModuleVersion> dependantComparator = new Comparator<ModuleVersion>() {
            @Override
            public int compare(ModuleVersion v1, ModuleVersion v2) {
                int result = v1.module.name.compareTo(v2.module.name);
                if (result == 0) {
                    result = Util.compareVersions(v1.version, v2.version);
                }
                return result;
            }
        };

        SortedMap<String, SortedSet<ModuleVersion>> dependantsMap = new TreeMap<String, SortedSet<ModuleVersion>>(versionComparator);
        for (Object[] result : results) {
            String version = (String) result[0];
            ModuleVersion dependant = (ModuleVersion) result[1];

            SortedSet<ModuleVersion> dependants = dependantsMap.get(version);
            if (dependants == null) {
                dependants = new TreeSet<ModuleVersion>(dependantComparator);
                dependantsMap.put(version, dependants);
            }
            dependants.add(dependant);
        }

        return dependantsMap;
    }
    
    public static long findDependantsCount(String moduleName) {
        return JPA.em()
                .createQuery("SELECT count(v) FROM ModuleVersion v JOIN v.dependencies d WHERE d.name=:name", Long.class)
                .setParameter("name", moduleName)
                .getSingleResult().longValue();
    }
	
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
        case ALL:
            return prefix+"isCarPresent = true OR "+prefix+"isJarPresent = true OR "+prefix+"isJsPresent = true OR "+prefix+"isSourcePresent = true";
        default:
            // ouch
            throw new RuntimeException("Invalid switch statement: missing enum cases");    
        }
    }

    public static List<ModuleVersion> completeVersionForModuleAndBackend(Module module, String version, Type type,
            Integer binaryMajor, Integer binaryMinor) {
        String typeQuery = ModuleVersion.getBackendQuery("", type);
        if(version == null)
            version = "";
        String binaryQuery = getBinaryQuery("", binaryMajor, binaryMinor);
        JPAQuery query = ModuleVersion.find("module = :module AND LOCATE(:version, version) = 1 AND ("+typeQuery+")"
                + binaryQuery
                + " ORDER BY version");
        query.bind("module", module);
        query.bind("version", version);
        addBinaryQueryParameters(query, binaryMajor, binaryMinor);
        
        return query.fetch(RepoAPI.RESULT_LIMIT);
    }

    static void addBinaryQueryParameters(JPAQuery query, Integer binaryMajor, Integer binaryMinor) {
        // Note that we use query.query.setParameter here rather than query.bindParameter because the latter
        // has a bug that turns Integer instances into Long instances (WTF?)
        if(binaryMajor != null)
            query.query.setParameter("binaryMajor", binaryMajor);
        if(binaryMinor != null)
            query.query.setParameter("binaryMinor", binaryMinor);
    }

    static String getBinaryQuery(String prefix, Integer binaryMajor, Integer binaryMinor) {
        if(binaryMajor == null && binaryMinor == null)
            return "";
        StringBuilder ret = new StringBuilder("AND (").append(prefix).append("isCarPresent = false OR (");
        // these only apply to ceylon modules
        if(binaryMajor != null)
            ret.append(prefix).append("ceylonMajor = :binaryMajor");
        if(binaryMinor != null){
            if(binaryMajor != null)
                ret.append(" AND ");
            ret.append(prefix).append("ceylonMinor = :binaryMinor");
        }
        ret.append("))");
        return ret.toString();
    }

    public boolean matchesBinaryVersion(Integer binaryMajor, Integer binaryMinor) {
        if(binaryMajor != null && ceylonMajor != binaryMajor)
            return false;
        if(binaryMinor != null && ceylonMinor != binaryMinor)
            return false;
        return true;
    }
    
}
