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

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@SuppressWarnings("serial")
@Entity
public class ModuleComment extends Model {
	
	public Date date;
	@ManyToOne
	public User owner;
	@ManyToOne
	public Module module;

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String text;
    
}
