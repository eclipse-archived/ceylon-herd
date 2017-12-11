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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import play.db.jpa.GenericModel;

@Entity
@SuppressWarnings("serial")
@Table(name = "herd_metainf")
public class HerdMetainf extends GenericModel {

    // IMPORTANT This number must be the same as the one found in
    // the latest `db/db-XX.sql` and `db/update-XX.sql` files !!!
    public static final int DB_SCHEMA_VERSION = 22;
    
    public static final String KEY_DB_SCHEMA_VERSION = "db_schema_version";
    
    @Id
    public Long id;

    public Long getId() {
        return id;
    }

    @Override
    public Object _key() {
        return getId();
    }
    
	@Column(nullable = false, unique = true)
	public String key;

    @Column
    public String value;

    public static HerdMetainf findByKey(String key) {
        return find("lower(key) = lower(?)", key).first();
    }
    
    public static int getDbSchemaVersion() {
        try {
            HerdMetainf result = findByKey(KEY_DB_SCHEMA_VERSION);
            if (result != null) {
                return Integer.valueOf(result.value);
            }
        } catch (Exception ex) {}
        return -1;
    }
    
    /**
     * Get's called in DEV mode to initialize the database table
     * with the proper information
     */
    public static void initialize() {
        if (getDbSchemaVersion() < 0) {
            HerdMetainf init = new HerdMetainf();
            init.id = 1L;
            init.key = KEY_DB_SCHEMA_VERSION;
            init.value = Integer.toString(DB_SCHEMA_VERSION);
            init.save();
        }
    }
}