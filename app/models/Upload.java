/********************************************************************************
 * Copyright (c) {date} Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import play.db.jpa.Model;
import util.JavaExtensions;
import util.Util;

@SuppressWarnings("serial")
@Entity
public class Upload extends Model {
	@ManyToOne
	public User owner;
	public Date created;

    @OneToMany(mappedBy = "upload", cascade = CascadeType.REMOVE)
    public List<MavenDependency> mavenDependencies = new ArrayList<MavenDependency>();

    @OneToMany(mappedBy = "upload", cascade = CascadeType.REMOVE)
    public List<HerdDependency> herdDependencies = new ArrayList<HerdDependency>();

	
	@Transient
	public long getSize(){
		return JavaExtensions.folderSize(Util.getUploadDir(id));
	}

	@Transient
	public long getFileCount(){
		return JavaExtensions.countFiles(Util.getUploadDir(id));
	}

	//
	// Static helpers
	
    public static Long countForOwner(User owner) {
        return count("owner = ?", owner);
    }

    public MavenDependency findMavenDependency(String name, String version) {
        for(MavenDependency md : mavenDependencies){
            if(md.name.equals(name) && md.version.equals(version))
                return md;
        }
        return null;
    }

    public HerdDependency findHerdDependency(String name, String version) {
        for(HerdDependency hd : herdDependencies){
            if(hd.name.equals(name) && hd.version.equals(version))
                return hd;
        }
        return null;
    }
}
