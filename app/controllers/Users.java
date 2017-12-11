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

import models.Project;
import models.User;
import play.data.validation.Required;
import play.mvc.Before;

import java.util.List;

public class Users extends MyController {

	// we set it if it's there, otherwise we don't require it
	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            renderArgs.put("user", user);
        }
    }

	public static void view(@Required String username){
		models.User viewedUser = models.User.findRegisteredByUserName(username);
		notFoundIfNull(viewedUser);
		
		List<Project> ownedProjects = viewedUser.getOwnedProjects();
		
		render(viewedUser, ownedProjects);
	}

}