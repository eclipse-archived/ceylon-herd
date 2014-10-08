package models;

import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.apache.commons.lang.StringUtils.removeEnd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;

import models.Module.QueryParams.Retrieval;
import models.Module.QueryParams.Suffix;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.Sort;
import org.hibernate.annotations.SortType;

import play.db.jpa.JPA;
import play.db.jpa.JPABase;
import play.db.jpa.Model;
import util.ApiVersion;
import util.VersionComparator;
import controllers.RepoAPI;

@Entity
@SuppressWarnings("serial")
public class Module extends Model {

    public static class QueryParams {
        public enum Suffix {
            CAR, JAR, JS, SRC, RESOURCES, DOCS, SCRIPTS_ZIPPED;
        }
        public enum Retrieval {
            ANY, ALL;
        }
        
        private Suffix[] suffixes;
        private Retrieval retrieval;
        
        public Integer binaryMajor;
        public Integer binaryMinor;
        public String memberName;
        public boolean memberSearchPackageOnly;
        public boolean memberSearchExact;
        
        // These are the old pre-4 Type enum equivalents
        public static QueryParams JVM() { return new QueryParams(Retrieval.ANY, Suffix.CAR, Suffix.JAR); }
        public static QueryParams CAR() { return new QueryParams(Retrieval.ANY, Suffix.CAR); }
        public static QueryParams JAR() { return new QueryParams(Retrieval.ANY, Suffix.JAR); }
        public static QueryParams JS() { return new QueryParams(Retrieval.ANY, Suffix.JS); }
        public static QueryParams SRC() { return new QueryParams(Retrieval.ANY, Suffix.SRC); }
        public static QueryParams CODE() { return new QueryParams(Retrieval.ANY, Suffix.CAR, Suffix.JAR, Suffix.JS); }
        public static QueryParams ALL() { return new QueryParams(Retrieval.ANY, Suffix.CAR, Suffix.JAR, Suffix.JS, Suffix.SRC); }
        
        public QueryParams(Retrieval retrieval, Suffix... suffixes) {
            this.suffixes = suffixes;
            this.retrieval = retrieval;
        }

        public Suffix[] getSuffixes() {
            return suffixes;
        }

        public Retrieval getRetrieval() {
            return retrieval;
        }        
    }

	public static final Pattern githubPattern = Pattern.compile("https?://github.com/([^/]+)/([^/]+)/?");

	@Column(nullable = false, unique = true)
	public String name;
	
	public String friendlyName;
	public String homeURL;
	public String issueTrackerURL;
	public String codeURL;
	
	@JoinColumn(nullable = false)
	@ManyToOne
	public User owner;
	
	@Sort(comparator = VersionComparator.class, type = SortType.COMPARATOR)
	@OneToMany(mappedBy = "module")
	public SortedSet<ModuleVersion> versions = new TreeSet<ModuleVersion>();

	@ManyToMany
    @JoinTable(name = "module_admin_user",
            	joinColumns = { @JoinColumn(name = "module") },
            	inverseJoinColumns = { @JoinColumn(name = "admin") })
	public List<User> admins = new ArrayList<User>();

	@OrderBy("date")
	@OneToMany(mappedBy="module")
	public List<ModuleComment> comments = new ArrayList<ModuleComment>();
	
    @OneToMany(mappedBy="module")
    public List<ModuleRating> ratings = new ArrayList<ModuleRating>();

    @ManyToOne
	public Category category;
	
	@Transient
	public boolean isGithub(){
		if(StringUtils.isEmpty(codeURL))
			return false;
		Matcher matcher = githubPattern.matcher(codeURL);
		return matcher.matches();
	}

	@Transient
	public String getGithubOwner(){
		if(StringUtils.isEmpty(codeURL))
			return null;
		Matcher matcher = githubPattern.matcher(codeURL);
		if(!matcher.matches())
			return null;
		return matcher.group(1);
	}

	@Transient
	public String getGithubProject(){
		if(StringUtils.isEmpty(codeURL))
			return null;
		Matcher matcher = githubPattern.matcher(codeURL);
		if(!matcher.matches())
			return null;
		return matcher.group(2);
	}

	@Transient
	public Date getLastPublished(){
		return find("SELECT MAX(published) FROM ModuleVersion WHERE module = ?", this).first(); 
	}
	
    @Transient
    public long getDownloads(){
        long ret = 0;
        for(ModuleVersion version : versions){
            ret += version.downloads;
        }
        return ret;
    }

    @Transient
    public long getJsDownloads(){
        long ret = 0;
        for(ModuleVersion version : versions){
            ret += version.jsdownloads;
        }
        return ret;
    }

	@Transient
	public long getSourceDownloads(){
	    long ret = 0;
	    for(ModuleVersion version : versions){
	        ret += version.sourceDownloads;
	    }
	    return ret;
	}

	@Transient
	public long getTotalDownloads(){
	    long ret = 0;
	    for(ModuleVersion version : versions){
	        ret += version.downloads;
            ret += version.jsdownloads;
            ret += version.sourceDownloads;
	    }
	    return ret;
	}

    @Transient
    public String getPath(){
        return name.replace('.', '/');
    }
    
    @Transient
    public long getRatingCount() {
        long count = 0;
        for (ModuleRating rating : ratings) {
            if (rating.mark > 0 && rating.mark < 6) {
                count++;
            }
        }
        return count;
    }

    @Transient
    public double getRatingAverage() {
        double sum = 0;
        long count = 0;
        for (ModuleRating rating : ratings) {
            if (rating.mark > 0 && rating.mark < 6) {
                sum += rating.mark;
                count++;
            }
        }
        return count != 0 ? sum / count : 0.0;
    }
    
    @Transient
    public ModuleRating getRatingFor(User user) {
        ModuleRating userRating = null;
        if (user != null && user.id != null) {
            for (ModuleRating rating : ratings) {
                if (rating.owner != null && rating.owner.id == user.id) {
                    userRating = rating;
                    break;
                }
            }
        }
        return userRating;
    }
    
    public boolean canEdit(User user){
		return user != null
				&& (user.equals(owner)
						|| user.isAdmin
						|| admins.contains(user));
	}
	
	public boolean isOwnedBy(User user){
		return user != null
				&& (user.equals(owner)
						|| user.isAdmin);
	}
    
	public List<ModuleVersion> getVersions(QueryParams params){
	    List<ModuleVersion> ret = new LinkedList<ModuleVersion>();
	    for(ModuleVersion version : versions){
	        boolean includeVersion = params.getRetrieval() == Retrieval.ALL;
	        for (Suffix suffix : params.getSuffixes()) {
    	        boolean include = false;
    	        switch(suffix){
                case CAR:
                    include = version.isCarPresent && version.matchesBinaryVersion(params.binaryMajor, params.binaryMinor);
                    break;
                case JAR:
                    include = version.isJarPresent;
                    break;
                case JS:
                    include = version.isJsPresent && version.matchesBinaryVersion(params.binaryMajor, params.binaryMinor);
                    break;
                case SRC:
                    include = version.isSourcePresent;
                    break;
                case RESOURCES:
                    include = version.isResourcesPresent;
                    break;
                case DOCS:
                    include = version.isDocPresent;
                    break;
                case SCRIPTS_ZIPPED:
                    include = version.isScriptsPresent;
                    break;
                default:
                    // ouch
                    throw new RuntimeException("Invalid switch statement: missing enum cases " + suffix);    
    	        }
    	        switch (params.getRetrieval()) {
    	        case ANY:
    	            includeVersion = includeVersion || include;
                    break;
                case ALL:
                    includeVersion = includeVersion && include;
                    break;
                default:
                    // ouch
                    throw new RuntimeException("Invalid switch statement: missing enum cases " + params.getRetrieval());    
    	        }
	        }
	        if(includeVersion)
	            ret.add(version);
	    }
	    return ret;
	}
	
	public ModuleVersion getLastVersion(){
	    return versions.isEmpty() ? null : versions.last();
	}

	public ModuleVersion getLastVersion(Integer binaryMajor, Integer binaryMinor){
	    // we can't use NavigableSet interface because Hibernate's SortedSet does not implement it
	    ModuleVersion[] array = new ModuleVersion[versions.size()];
	    versions.toArray(array);
	    for(int i=array.length-1;i>=0;i--){
	        ModuleVersion version = array[i];
	        if(version.matchesBinaryVersion(binaryMajor, binaryMinor))
	            return version;
	    }
	    return null;
	}

	@Override
	public <T extends JPABase> T delete() {
	    JPA.em().createNativeQuery("DELETE FROM dependency d WHERE d.id IN (SELECT d.id FROM dependency d, moduleversion v WHERE d.moduleversion_id = v.id AND v.module_id = :moduleId)").setParameter("moduleId", id).executeUpdate();
	    JPA.em().createNativeQuery("DELETE FROM moduleversion_author a WHERE a.moduleversion_id IN (SELECT v.id FROM moduleversion v WHERE v.module_id = :moduleId);").setParameter("moduleId", id).executeUpdate();
        JPA.em().createNativeQuery("DELETE FROM modulemember m WHERE m.moduleversion_id IN (SELECT v.id FROM moduleversion v WHERE v.module_id = :moduleId);").setParameter("moduleId", id).executeUpdate();
	    JPA.em().createNativeQuery("DELETE FROM moduleversion v WHERE v.module_id = :moduleId").setParameter("moduleId", id).executeUpdate();
	    JPA.em().createNativeQuery("DELETE FROM modulecomment c WHERE c.module_id = :moduleId").setParameter("moduleId", id).executeUpdate();
	    JPA.em().createNativeQuery("DELETE FROM module_admin_user u WHERE u.module = :moduleId").setParameter("moduleId", id).executeUpdate();
	    return super.delete();
	}
	
	//
	// Static helpers
	
    public static List<Module> findAllFetchOwnerAndVersions() {
        return find("SELECT DISTINCT m FROM Module m " +
                "LEFT JOIN FETCH m.owner " +
                "LEFT JOIN FETCH m.versions " +
                "LEFT JOIN FETCH m.ratings " +
                "ORDER BY m.name").fetch();
    }

    public static List<Module> findMostPopular() {
        return find("SELECT m FROM Module m " +
                "RIGHT OUTER JOIN m.ratings AS ratings " +
                "GROUP BY m.id, m.category, m.codeURL, m.friendlyName, m.homeURL, m.issueTrackerURL, m.name, m.owner " +
                "ORDER BY SUM(ratings.mark) DESC").fetch();
    }

    public static List<Module> findMostDownloaded() {
        return find("SELECT m FROM Module m " +
                "RIGHT OUTER JOIN m.versions AS versions " +
                "GROUP BY m.id, m.category, m.codeURL, m.friendlyName, m.homeURL, m.issueTrackerURL, m.name, m.owner " +
                "ORDER BY SUM(versions.downloads + versions.jsdownloads + versions.sourceDownloads) DESC").fetch();
    }

    public static List<Module> findByCategoryFetchOwnerAndVersions(Category category) {
        return find("SELECT DISTINCT m FROM Module m " +
                "LEFT JOIN FETCH m.owner " +
                "LEFT JOIN FETCH m.versions " +
                "LEFT JOIN FETCH m.ratings " +
                "WHERE m.category.id = ? " +
                "ORDER BY m.name", category.id).fetch();
    }
	
	public static Module findByName(String moduleName) {
		return find("name = ?", moduleName).first();
	}

    public static List<Module> searchByName(String q) {
        return find("SELECT DISTINCT m FROM Module m " +
                "LEFT JOIN FETCH m.owner " +
                "LEFT JOIN FETCH m.versions " +
                "LEFT JOIN FETCH m.ratings " +
                "WHERE LOCATE(?, m.name) <> 0 " +
                "ORDER BY m.name", q).fetch();
    }
    
    public static List<Module> searchByCriteria(String name, String friendlyName, String member, String license, String category) {
        String q = "SELECT DISTINCT m FROM Module m ";
        if(isNotEmpty(member)){
            q += "LEFT OUTER JOIN m.versions as v LEFT OUTER JOIN v.members as memb ";
        }
        if (isNotEmpty(license)) {
            q += ",Project p ";
        }
        q += "LEFT JOIN FETCH m.owner ";
        if(!isNotEmpty(member)){
            q += "LEFT JOIN FETCH m.versions ";
        }
        q += "LEFT JOIN FETCH m.ratings ";
        q += "WHERE ";
        if (isNotEmpty(name)) {
            q += "LOCATE(LOWER(:name), LOWER(m.name)) <> 0 AND ";
        }
        if (isNotEmpty(friendlyName)) {
            q += "LOCATE(LOWER(:friendlyName), LOWER(m.friendlyName)) <> 0 AND ";
        }
        if (isNotEmpty(member)) {
            q += "(LOCATE(LOWER(:member), LOWER(memb.packageName)) <> 0 OR LOCATE(LOWER(:member), LOWER(memb.name)) <> 0) AND ";
        }
        if (isNotEmpty(license)) {
            q += "m.name = p.moduleName AND LOCATE(LOWER(:license), LOWER(p.license)) <> 0 AND ";
        }
        if (isNotEmpty(category)) {
            q += "LOCATE(LOWER(:category), LOWER(m.category.name)) <> 0 AND ";
        }
        q = removeEnd(q, "AND ");
        q += "ORDER BY m.name";

        JPAQuery jpaQuery = find(q);
        if (isNotEmpty(name)) {
            jpaQuery.bind("name", name);
        }
        if (isNotEmpty(friendlyName)) {
            jpaQuery.bind("friendlyName", friendlyName);
        }
        if (isNotEmpty(license)) {
            jpaQuery.bind("license", license);
        }
        if (isNotEmpty(category)) {
            jpaQuery.bind("category", category);
        }
        if (isNotEmpty(member)) {
            jpaQuery.bind("member", member);
        }

        return jpaQuery.fetch();
    }
    
	public static List<Module> findByOwner(User owner) {
		return find("owner = ? ORDER BY name", owner).fetch();
	}

	public static long countForOwner(User owner) {
		return count("owner = ?", owner);
	}

    public static List<Module> completeForBackend(String module, QueryParams params) {
        JPAQuery query = createCompleteForBackend(false, module, params);
        return query.fetch(RepoAPI.RESULT_LIMIT);
    }

    public static long completeForBackendCount(String module, QueryParams params) {
        JPAQuery query = createCompleteForBackend(true, module, params);
        return query.first();
    }

    private static JPAQuery createCompleteForBackend(boolean selectCount, String module, QueryParams params) {
        if(module == null)
            module = "";
        String typeQuery = ModuleVersion.getBackendQuery("v.", params);
        String binaryQuery = ModuleVersion.getBinaryQuery("v.", params.binaryMajor, params.binaryMinor);

        String select = "";
        if (selectCount) {
            select += "SELECT COUNT(DISTINCT m) ";
        } else {
            select += "SELECT DISTINCT m ";
        }
        
        select += "FROM Module m ";
        
        String where = "WHERE LOCATE(:module, m.name) = 1"
                + " AND EXISTS(";

        String subselect = "FROM ModuleVersion v ";
        String subwhere = "WHERE v.module = m AND ("+typeQuery+")"+binaryQuery;
        
        if (isNotEmpty(params.memberName)) {
            subselect += "LEFT JOIN v.members as memb ";
            subwhere += ModuleVersion.getMemberQuery("memb.", params);
        }
        
        where += subselect + subwhere;
        where += ")";
        
        String q = select + where;
        if (!selectCount) {
            q += "ORDER BY m.name ";
        }
        JPAQuery query = Module.find(q);
        query.bind("module", module);
        if (isNotEmpty(params.memberName)) {
            query.bind("memberName", params.memberName.toLowerCase());
        }
        ModuleVersion.addBinaryQueryParameters(query, params.binaryMajor, params.binaryMinor);
                
        return query;
    }

    public static List<Module> searchForBackend(ApiVersion v, String query, QueryParams params, Integer start, Integer count) {
        if (count == 0) {
            return Collections.<Module> emptyList();
        }
        JPAQuery q = createQueryForBackend(v, false, query, params);
        return q.from(start).fetch(count);
    }
    
    public static long searchForBackendCount(ApiVersion v, String query, QueryParams params) {
        JPAQuery q = createQueryForBackend(v, true, query, params);
        return q.first();
    }
    
    private static JPAQuery createQueryForBackend(ApiVersion v, boolean selectCount, String query, QueryParams params) {
        String typeQuery = ModuleVersion.getBackendQuery("v.", params);
        String binaryQuery = ModuleVersion.getBinaryQuery("v.", params.binaryMajor, params.binaryMinor);
        
        String select = "";
        if (selectCount) {
            select += "SELECT COUNT(DISTINCT m) ";
        } else {
            select += "SELECT DISTINCT m ";
        }
        
        select += "FROM Module m " +
             "LEFT JOIN m.versions as v ";
        
        String where = "WHERE (" + typeQuery + ")" + binaryQuery + " ";

        if (isNotEmpty(query)) {
            if (v.ordinal() >= ApiVersion.API4.ordinal()) {
                select += "LEFT JOIN v.authors as auth " +
                            "LEFT JOIN v.dependencies as dep ";
            }
            where += "AND (LOCATE(:query, LOWER(m.name)) <> 0 " +
                        "OR LOCATE(:query, LOWER(v.doc)) <> 0 ";
            if (v.ordinal() >= ApiVersion.API4.ordinal()) {
                where += "OR LOCATE(:query, LOWER(v.license)) <> 0 " +
                        "OR LOCATE(:query, LOWER(auth.name)) <> 0 " +
                        "OR LOCATE(:query, LOWER(dep.name)) <> 0 ";
            }
            where += ")";
        }
        
        if (isNotEmpty(params.memberName)) {
            select += "LEFT JOIN v.members as memb ";
            where += ModuleVersion.getMemberQuery("memb.", params);
        }
        
        String q = select + where;
        if (!selectCount) {
            q += "ORDER BY m.name ";
        }
        
        JPAQuery jpaQuery = Module.find(q);
        if (isNotEmpty(query)) {
            jpaQuery.bind("query", query.toLowerCase());
        }
        if (isNotEmpty(params.memberName)) {
            jpaQuery.bind("memberName", params.memberName.toLowerCase());
        }
        ModuleVersion.addBinaryQueryParameters(jpaQuery, params.binaryMajor, params.binaryMinor);

        return jpaQuery;
    }

}