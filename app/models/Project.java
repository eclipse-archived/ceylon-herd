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
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;

import play.db.jpa.Model;
import util.Util;

@SuppressWarnings("serial")
@Entity
public class Project extends Model {

	@Column(nullable = false)
	public String moduleName;
	
	@ManyToOne
	@JoinColumn(nullable = false)
	public User owner;
	
	@Enumerated(EnumType.STRING)
	public ProjectStatus status;
	
	public String license;
	public String role;
	// Hibernate would map @Lob to a CLOB instead of TEXT
	@Column(columnDefinition = "TEXT")
	public String description;
    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String motivation;
	public String url;
	
	@OrderBy("date")
	@OneToMany(mappedBy = "project", cascade = CascadeType.REMOVE)
	public List<Comment> comments = new ArrayList<Comment>();
	
	public boolean canBeAccepted(){
		return status != ProjectStatus.CONFIRMED;
	}
	public boolean canBeRejected(){
		return status != ProjectStatus.REJECTED;
	}
    public boolean canBeEdited(){
        return status == ProjectStatus.CLAIMED;
    }
	
	@Transient
	public Module getModule(){
		return Module.findByName(moduleName);
	}
	
	//
	// Static helpers
	
	public static Project findOwner(String moduleName) {
		return find("moduleName = ? AND status = ?", moduleName, ProjectStatus.CONFIRMED).first();
	}

	public static Project findForOwner(String moduleName, User owner) {
		return find("moduleName = ? AND owner = ?", moduleName, owner).first();
	}
	
    public static Long countForOwner(User owner) {
        return count("owner = ?", owner);
    }
    
    public static long countClaims() {
        return count("status = ?", ProjectStatus.CLAIMED);
    }
    
    public static List<Project> findPendingClaims() {
        return find("status = ? ORDER BY moduleName", ProjectStatus.CLAIMED).fetch();
    }

    public static List<Project> findAllClaims(int page) {
        return find("ORDER BY moduleName").fetch(page, Util.PAGE_SIZE);
    }

    public static List<Project> findAllMatchingClaims(String q, int page) {
        return find("LOCATE(?, moduleName) <> 0 ORDER BY moduleName", q).fetch(page, Util.PAGE_SIZE);
    }

    public static long countAllMatchingClaims(String q) {
        return count("LOCATE(?, moduleName) <> 0", q);
    }

    public static List<Project> findForOwner(User owner) {
        return find("owner = ? AND status = ? ORDER BY moduleName", owner, ProjectStatus.CONFIRMED).fetch();
    }
}
