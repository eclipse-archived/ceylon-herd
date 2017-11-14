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

import javax.persistence.Column;
import javax.persistence.Entity;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class Author extends Model {

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String name;

	//
	// Static helpers
	
	public static Author findOrCreate(String name) {
		Author author = find("name = ?", name).first();
		if(author == null){
		    author = new Author();
		    author.name = name;
		    author.create();
		}
		return author;
	}
}
