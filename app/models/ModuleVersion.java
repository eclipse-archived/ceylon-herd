package models;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Sort;
import org.hibernate.annotations.SortType;

import models.Module.QueryParams;
import models.Module.QueryParams.Retrieval;
import models.Module.QueryParams.Suffix;
import play.db.jpa.JPA;
import play.db.jpa.Model;
import util.CeylonElementType;
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
    public boolean isScriptsPresent;
    public boolean isResourcesPresent;
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
	
    public int jvmBinMajor;
    public int jvmBinMinor;
    public int jsBinMajor;
    public int jsBinMinor;

	@OrderBy("name,version")
	@OneToMany(mappedBy = "moduleVersion", cascade = CascadeType.REMOVE)
    private List<Dependency> dependencies = new ArrayList<Dependency>();

    @Sort(comparator = ModuleMemberComparator.class, type = SortType.COMPARATOR)
    @OrderBy("packageName,name")
	@OneToMany(mappedBy = "moduleVersion", cascade = CascadeType.REMOVE)
	private SortedSet<ModuleMember> members = new TreeSet<ModuleMember>();

    @OrderBy("name")
    @ManyToMany
    public List<Author> authors = new ArrayList<Author>();

    @Transient
	public String getPath(){
		return module.name.replace('.', '/')+"/"+version;
	}

	@Transient
	public String getAPIPath(){
		return getPath() + "/module-doc/api/index.html";
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
    public String getScriptsPath(){
        return getPath() + "/" + module.name + "-" + version + ".scripts.zip";
    }

	@Transient
	public String getDocPath(){
	    return getPath() + "/" + "module-doc.zip";
	}

    public void addDependency(String name, String version, boolean optional, boolean export, boolean resolvedFromMaven) {
        Dependency dep = new Dependency(this, name, version, optional, export, resolvedFromMaven);
        dep.create();
        dependencies.add(dep);
    }

    public void addMember(String packageName, String name, CeylonElementType type) {
        ModuleMember member = new ModuleMember(this, packageName, name, type);
        member.create();
        members.add(member);
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
    
    public static SortedMap<String, SortedSet<ModuleVersion>> findDependants(String moduleName, String version) {
        String query = "SELECT d.version, v FROM ModuleVersion v JOIN v.dependencies d LEFT JOIN FETCH v.module WHERE d.name=:name";
        if (version != null && !version.isEmpty()) {
            query += " AND d.version=:version";
        }
        Query jpa = JPA.em()
                .createQuery(query)
                .setParameter("name", moduleName);
        if (version != null && !version.isEmpty()) {
            jpa.setParameter("version", version);
        }
        List<Object[]> results = jpa.getResultList();

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
            String ver = (String) result[0];
            ModuleVersion dependant = (ModuleVersion) result[1];

            SortedSet<ModuleVersion> dependants = dependantsMap.get(ver);
            if (dependants == null) {
                dependants = new TreeSet<ModuleVersion>(dependantComparator);
                dependantsMap.put(ver, dependants);
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

    static String getBackendQuery(String prefix, QueryParams type){
        StringBuilder query = new StringBuilder();
        boolean first = true;
        for (Suffix suffix : type.getSuffixes()) {
            if (!first) {
                switch (type.getRetrieval()) {
                case ANY:
                    query.append(" OR ");
                    break;
                case ALL:
                    query.append(" AND ");
                    break;
                default:
                    // ouch
                    throw new RuntimeException("Invalid switch statement: missing enum cases " + type.getRetrieval());    
                }
            }
            String q;
            switch(suffix){
            case CAR:
                q = prefix+"isCarPresent = true";
                break;
            case JAR:
                q = prefix+"isJarPresent = true";
                break;
            case JS:
                q = prefix+"isJsPresent = true";
                break;
            case SRC:
                q = prefix+"isSourcePresent = true";
                break;
            case RESOURCES:
                q = prefix+"isSourcePresent = true";
                break;
            case DOCS:
                q = prefix+"isSourcePresent = true";
                break;
            case SCRIPTS_ZIPPED:
                q = prefix+"isSourcePresent = true";
                break;
            default:
                // ouch
                throw new RuntimeException("Invalid switch statement: missing enum cases " + suffix);    
            }
            query.append(q);
            first = false;
        }
        return query.toString();
    }

    public static List<ModuleVersion> completeVersionForModuleAndBackend(Module module, String version, QueryParams params) {
        String typeQuery = getBackendQuery("v.", params);
        if(version == null)
            version = "";
        String binaryQuery = getBinaryQuery("v.", params.binaryMajor, params.binaryMinor);
        
        String select = "SELECT DISTINCT v FROM ModuleVersion v ";
        String where = "WHERE v.module = :module AND LOCATE(:version, v.version) = 1 AND ("+typeQuery+")"
                + binaryQuery;
        
        if (isNotEmpty(params.memberName)) {
            select += "LEFT JOIN v.members as memb ";
            where += getMemberQuery("memb.", params);
        }
        
        String q = select + where + " ORDER BY v.version";
        JPAQuery query = ModuleVersion.find(q);
        
        query.bind("module", module);
        query.bind("version", version);
        if (isNotEmpty(params.memberName)) {
            query.bind("memberName", params.memberName.toLowerCase());
        }
        addBinaryQueryParameters(query, params.binaryMajor, params.binaryMinor);
        
        return query.fetch(RepoAPI.RESULT_LIMIT);
    }

    static String getMemberQuery(String prefix, QueryParams params) {
        String where = "";
        if (isNotEmpty(params.memberName)) {
            if (params.memberSearchPackageOnly) {
                if (params.memberSearchExact) {
                    where += "AND LOWER("+prefix+"packageName) = :memberName ";
                } else {
                    where += "AND LOCATE(:memberName, LOWER("+prefix+"packageName)) <> 0 ";
                }
            } else {
                if (params.memberSearchExact) {
                    where += "AND LOWER(CONCAT("+prefix+"packageName, '::', "+prefix+"name)) = :memberName ";
                } else {
                    where += "AND LOCATE(:memberName, LOWER(CONCAT("+prefix+"packageName, '::', "+prefix+"name))) <> 0 ";
                }
            }
        }
        return where;
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
        StringBuilder ret = new StringBuilder("AND (").append(prefix).append("isCarPresent = false AND ").append(prefix).append("isJsPresent = false OR (");
        // these only apply to ceylon modules
        if(binaryMajor != null)
            ret.append(prefix).append("jvmBinMajor = :binaryMajor");
        if(binaryMinor != null){
            if(binaryMajor != null)
                ret.append(" AND ");
            ret.append(prefix).append("jsBinMinor = :binaryMinor");
        }
        ret.append(") OR (");
        if(binaryMajor != null)
            ret.append(prefix).append("jsBinMajor = :binaryMajor");
        if(binaryMinor != null){
            if(binaryMajor != null)
                ret.append(" AND ");
            ret.append(prefix).append("jsBinMinor = :binaryMinor");
        }
        ret.append("))");
        return ret.toString();
    }

    public boolean matchesBinaryVersion(Integer binaryMajor, Integer binaryMinor) {
        if(binaryMajor != null && jvmBinMajor != binaryMajor && jsBinMajor != binaryMajor)
            return false;
        if(binaryMinor != null && jvmBinMinor != binaryMinor && jsBinMinor != binaryMinor)
            return false;
        return true;
    }
    
}
