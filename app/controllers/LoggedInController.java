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

import models.User;
import play.mvc.Before;
import play.mvc.With;

@With(Secure.class)
public class LoggedInController extends MyController {
	
	@Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.findRegisteredByUserName(Security.connected());
            renderArgs.put("user", user);
        }
    }
	
}
