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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class Category extends Model {

	@Column(nullable = false, unique = true)
	public String name;

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String description;
    
    @OrderBy("name")
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    public List<Module> modules = new ArrayList<Module>();

    public static List<Category> findAllCategories() {
        return find("ORDER BY name").fetch();
    }
    
    public static List<String> findAllCategoriesNames() {
        return find("SELECT c.name FROM Category c ORDER BY c.name").fetch();
    }

    public static Category findByName(String name) {
        return find("lower(name) = lower(?)", name).first();
    }
    
}