/********************************************************************************
 * Copyright (c) {date} Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package controllers;

import java.util.List;

import models.User;

@Check("admin")
public class AdminUsers extends LoggedInController {

	public static void index(int page, int pageSize) {
    	if (page < 1) {
    		page = 1;
    	}
    	if (pageSize < 1 || pageSize > 50) {
    		pageSize = 20;
    	}
    	List<User> users = User.all().fetch(page, pageSize);
    	long userCount = User.count();
		int pageCount = (int) Math.ceil((float) userCount / pageSize);
		render(users, page, pageCount, pageSize);
	}
    
}
