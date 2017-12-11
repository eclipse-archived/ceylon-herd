/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package controllers;

import java.util.Date;
import java.util.List;

import models.Module;
import models.ModuleVersion;
import models.User;

public class Feed extends MyController {

    public static void repo(){
        List<ModuleVersion> moduleVersions = ModuleVersion.latest(10);
        Date lastPublished = getLastPublished(moduleVersions);
        render("Feed/repo.xml", moduleVersions, lastPublished);
    }

    public static void module(String moduleName){
        Module module = Module.findByName(moduleName);
        notFoundIfNull(module, "Unknown module "+moduleName);
        List<ModuleVersion> moduleVersions = ModuleVersion.latestForModule(moduleName, 10);
        Date lastPublished = getLastPublished(moduleVersions);
        render("Feed/module.xml", module, moduleVersions, lastPublished);
    }

    public static void user(String username){
        User user = User.findByUserName(username);
        notFoundIfNull(user, "Unknown user "+username);
        List<ModuleVersion> moduleVersions = ModuleVersion.latestForOwner(user, 10);
        Date lastPublished = getLastPublished(moduleVersions);
        render("Feed/user.xml", user, moduleVersions, lastPublished);
    }

    private static Date getLastPublished(List<ModuleVersion> moduleVersions) {
        Date lastPublished = null;
        for(ModuleVersion moduleVersion : moduleVersions){
            if(lastPublished == null
                    || (moduleVersion.published != null
                        && moduleVersion.published.after(lastPublished)))
                lastPublished = moduleVersion.published;
        }
        return lastPublished;
    }
}
