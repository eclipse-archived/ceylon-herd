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

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

// TODO:  Figure out what needs to go in here, exactly
@Entity
@SuppressWarnings("serial")
public class HerdDependency extends Model {

    public String name;
	public String version;

	@ManyToOne
	public Upload upload;

    public HerdDependency(String name, String version, Upload upload) {
        this.name = name;
        this.version = version;
        this.upload = upload;
    }
}
