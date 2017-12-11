/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package models;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

import controllers.RepoAPI;
import models.Module.QueryParams;
import models.Module.QueryParams.Suffix;
import play.db.jpa.JPA;
import play.db.jpa.Model;
import util.CeylonElementType;
import util.MavenVersionComparator;
import util.Util;

@Entity
@SuppressWarnings("serial")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"module_id", "version"}))
public class ModuleVersion extends Model implements Comparable<ModuleVersion> {

	private static final Comparator<ModuleVersion> ArtifactIdAndVersionComparator = new Comparator<ModuleVersion>(){

        @Override
        public int compare(ModuleVersion a, ModuleVersion b) {
            int ret = a.getVirtualArtifactId().compareTo(b.getVirtualArtifactId());
            if(ret != 0)
                return ret;
            return a.version.compareTo(b.version);
        }
	    
	};

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
    public boolean isNativeJvm;
    public boolean isNativeJs;
    public boolean isPackageJsonPresent;
    public String groupId;
    public String artifactId;
	
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
    public List<Dependency> dependencies = new ArrayList<Dependency>();

    @Sort(comparator = ModuleMemberComparator.class, type = SortType.COMPARATOR)
    @OrderBy("packageName,name")
	@OneToMany(mappedBy = "moduleVersion", cascade = CascadeType.REMOVE)
	private SortedSet<ModuleMember> members = new TreeSet<ModuleMember>();

    @Sort(comparator = ModuleScriptComparator.class, type = SortType.COMPARATOR)
    @OrderBy("name,plugin,unix")
    @OneToMany(mappedBy = "moduleVersion", cascade = CascadeType.REMOVE)
    private SortedSet<ModuleScript> scripts = new TreeSet<ModuleScript>();

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
	public String getAPIRootPath(){
	    return getPath() + "/module-doc/api";
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

    public void addDependency(String name, String version, boolean optional, boolean export, 
            boolean resolvedFromMaven, boolean resolvedFromHerd, 
            boolean nativeJvm, boolean nativeJs) {
        Dependency dep = new Dependency(this, name, version, optional, export, 
                resolvedFromMaven, resolvedFromHerd, nativeJvm, nativeJs);
        dep.create();
        dependencies.add(dep);
    }

    public void addMember(String packageName, String name, CeylonElementType type, boolean shared) {
        ModuleMember member = new ModuleMember(this, packageName, name, type, shared);
        member.create();
        members.add(member);
    }

    public void addScript(String name, String description, boolean unix, boolean plugin, String pluginModule) {
        ModuleScript script = new ModuleScript(this, name, description, unix, plugin, pluginModule);
        script.create();
        scripts.add(script);
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
        return MavenVersionComparator.compareVersions(version, other.version);
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
        String binaryQuery = getBinaryQuery("v.", params.jvmBinaryMajor, params.jvmBinaryMinor, 
                params.jsBinaryMajor, params.jsBinaryMinor);
        
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
        addBinaryQueryParameters(query, params.jvmBinaryMajor, params.jvmBinaryMinor,
                params.jsBinaryMajor, params.jsBinaryMinor);
        
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
    
    static void addBinaryQueryParameters(JPAQuery query, Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor) {
        // Note that we use query.query.setParameter here rather than query.bindParameter because the latter
        // has a bug that turns Integer instances into Long instances (WTF?)
        if(jvmBinaryMajor != null)
            query.query.setParameter("jvmBinaryMajor", jvmBinaryMajor);
        if(jvmBinaryMinor != null)
            query.query.setParameter("jvmBinaryMinor", jvmBinaryMinor);
        if(jsBinaryMajor != null)
            query.query.setParameter("jsBinaryMajor", jsBinaryMajor);
        if(jsBinaryMinor != null)
            query.query.setParameter("jsBinaryMinor", jsBinaryMinor);
    }

    static String getBinaryQuery(String prefix, 
            Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor) {
        if(jvmBinaryMajor == null && jvmBinaryMinor == null
                && jsBinaryMajor == null && jsBinaryMinor == null)
            return "";
        StringBuilder ret = new StringBuilder("AND ((")
                .append(prefix).append("isCarPresent = false AND ")
                .append(prefix).append("isJsPresent = false) OR (");
        // these only apply to ceylon modules
        
        if(jvmBinaryMajor != null || jvmBinaryMinor != null)
            ret.append(prefix).append("isCarPresent = false OR (");
        if(jvmBinaryMajor != null)
            ret.append(prefix).append("jvmBinMajor = :jvmBinaryMajor");
        if(jvmBinaryMinor != null){
            if(jvmBinaryMajor != null)
                ret.append(" AND ");
            ret.append(prefix).append("jvmBinMinor = :jvmBinaryMinor");
        }
        if(jvmBinaryMajor != null || jvmBinaryMinor != null)
            ret.append(")");
        
        // only put the AND if we have both tests, otherwise we have a single test and we're fine
        if((jvmBinaryMajor != null || jvmBinaryMinor != null)
                && (jsBinaryMajor != null || jsBinaryMinor != null))
            ret.append(") AND (");
        
        if(jsBinaryMajor != null || jsBinaryMinor != null)
            ret.append(prefix).append("isJsPresent = false OR (");
        if(jsBinaryMajor != null)
            ret.append(prefix).append("jsBinMajor = :jsBinaryMajor");
        if(jsBinaryMinor != null){
            if(jsBinaryMajor != null)
                ret.append(" AND ");
            ret.append(prefix).append("jsBinMinor = :jsBinaryMinor");
        }
        if(jsBinaryMajor != null || jsBinaryMinor != null)
            ret.append(")");
        ret.append("))");
        return ret.toString();
    }

    public boolean matchesBinaryVersion(Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor) {
        if(!isCarPresent && !isJsPresent)
            return true;
        if(isJsPresent){
            if(jsBinaryMajor != null && jsBinMajor != jsBinaryMajor)
                return false;
            if(jsBinaryMinor != null && jsBinMinor != jsBinaryMinor)
                return false;
        }
        if(isCarPresent){
            if(jvmBinaryMajor != null && jvmBinMajor != jvmBinaryMajor)
                return false;
            if(jvmBinaryMinor != null && jvmBinMinor != jvmBinaryMinor)
                return false;
        }
        return true;
    }

    @Transient
    public Set<String> getPackages(){
        Set<String> ret = new HashSet<String>();
        for(ModuleMember member : members){
            ret.add(member.packageName);
        }
        return ret;
    }

    public boolean containsPackage(String packageName) {
        return getPackages().contains(packageName);
    }
    
    @Transient
    public List<Dependency> getJvmDependencies(){
        List<Dependency> ret = new ArrayList<>(dependencies.size());
        for(Dependency dep : dependencies){
            if(dep.nativeJvm)
                ret.add(dep);
        }
        return ret;
    }

    @Transient
    public List<Dependency> getJsDependencies(){
        List<Dependency> ret = new ArrayList<>(dependencies.size());
        for(Dependency dep : dependencies){
            if(dep.nativeJs)
                ret.add(dep);
        }
        return ret;
    }

    @Transient
    public List<Dependency> getCommonDependencies(){
        List<Dependency> ret = new ArrayList<>(dependencies.size());
        for(Dependency dep : dependencies){
            if(!dep.nativeJvm && !dep.nativeJs)
                ret.add(dep);
        }
        return ret;
    }
    
    @Transient
    public String getMavenCoordinates(){
        if(groupId != null){
            String art = artifactId != null ? artifactId : module.name;
            return groupId+":"+art;
        }
        return null;
    }

    @Transient
    public String getVirtualGroupId() {
        if(groupId != null){
            return groupId;
        }
        int lastDot = module.name.lastIndexOf('.');
        // just repeat the module name
        if(lastDot == -1)
            return module.name;
        return module.name.substring(0, lastDot);
    }

    @Transient
    public String getVirtualArtifactId() {
        if(groupId != null){
            if(artifactId != null)
                return artifactId;
            return module.name;
        }
        int lastDot = module.name.lastIndexOf('.');
        // just repeat the module name
        if(lastDot == -1)
            return module.name;
        return module.name.substring(lastDot+1);
    }

    //
    // Util
    
    public static SortedSet<ModuleVersion> findByGroupId(String groupId) {
        List<ModuleVersion> mavenModules = find("(isCarPresent = true OR isJarPresent = true) AND groupId = :groupId")
                .bind("groupId", groupId)
                .fetch();
        List<ModuleVersion> ceylonModules = find("(isCarPresent = true OR isJarPresent = true) AND LOCATE(:start, module.name) = 1")
                .bind("start", groupId+".")
                .fetch();
        SortedSet<ModuleVersion> ret = new TreeSet<>(ArtifactIdAndVersionComparator);
        ret.addAll(mavenModules);
        for (ModuleVersion mv : ceylonModules) {
            if(mv.getVirtualGroupId().equals(groupId))
                ret.add(mv);
        }
        return ret;
    }

    public static SortedSet<ModuleVersion> findByMavenCoordinates(String groupId, String artifactId) {
        List<ModuleVersion> mavenModules = find("(isCarPresent = true OR isJarPresent = true)"
                +" AND ((groupId = :groupId AND (artifactId = :artifactId OR (artifactId IS NULL AND module.name = :artifactId)))"
                +" OR (groupId IS NULL AND module.name = :name))")
                .bind("groupId", groupId)
                .bind("artifactId", artifactId)
                .bind("name", groupId+"."+artifactId)
                .fetch();
        SortedSet<ModuleVersion> ret = new TreeSet<>(ArtifactIdAndVersionComparator);
        ret.addAll(mavenModules);
        return ret;
    }

    public static ModuleVersion findByMavenCoordinates(String groupId, String artifactId, String version) {
        return find("(isCarPresent = true OR isJarPresent = true)"
                +" AND version = :version"
                +" AND ((groupId = :groupId AND (artifactId = :artifactId OR (artifactId IS NULL AND module.name = :artifactId)))"
                +" OR (groupId IS NULL AND module.name = :name))")
                .bind("groupId", groupId)
                .bind("artifactId", artifactId)
                .bind("version", version)
                .bind("name", groupId+"."+artifactId)
                .first();
    }

    public static SortedSet<String> findGroupIdPrefixes(String start) {
        List<String> mavenPrefixes = find("SELECT DISTINCT mv.groupId AS prefix"
                +" FROM ModuleVersion mv"
                +" WHERE (mv.isCarPresent = true OR mv.isJarPresent = true) AND mv.groupId IS NOT NULL AND LOCATE(:start, mv.groupId) = 1"
                +" ORDER BY prefix").bind("start", start).fetch();
        List<String> ceylonPrefixes = find("SELECT DISTINCT mv.module.name AS prefix"
                +" FROM ModuleVersion mv"
                +" WHERE (mv.isCarPresent = true OR mv.isJarPresent = true) AND mv.groupId IS NULL AND LOCATE(:start, mv.module.name) = 1"
                +" ORDER BY prefix").bind("start", start).fetch();
        SortedSet<String> ret = new TreeSet<>();
        for (String prefix : mavenPrefixes) {
            prefix = prefix.substring(start.length());
            int firstDot = prefix.indexOf('.');
            if(firstDot != -1){
                ret.add(prefix.substring(0, firstDot));
            }else{
                ret.add(prefix);
            }
        }
        for (String prefix : ceylonPrefixes) {
            prefix = prefix.substring(start.length());
            int firstDot = prefix.indexOf('.');
            // only accept it if we have a dot, the last dot will separate our artifactId
            if(firstDot != -1){
                ret.add(prefix.substring(0, firstDot));
            }
        }
        return ret;
    }
}
