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

import java.util.UUID;

import models.User;
import models.UserStatus;
import notifiers.Emails;

import org.apache.commons.lang.StringUtils;

import play.Play;
import play.data.validation.Email;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.Crypto;
import util.Util;

public class Register extends MyController {

    private static boolean canRegister() {
        if (Security.isConnected()) {
            User user = User.findByUserName(Security.connected());
            if (user != null) {
                renderArgs.put("user", user);
                if(user.isAdmin)
                    return true;
            }
        }
        if ("true".equals(Play.configuration.get("register.enabled"))) {
            return true;
        }
        return false;
    }
    
    public static void index() {
        boolean canRegister = canRegister();
        render(canRegister);
    }

    public static void register(@Required @MaxSize(Util.VARCHAR_SIZE) @Email String email) {
        if (!canRegister()) {
            badRequest();
        }
        
    	if(validationFailed())
    		index();
    	User newUser = User.find("email = ? AND status = ?", email, UserStatus.CONFIRMATION_REQUIRED).first();
    	if(newUser == null){
    		newUser = new User();
    		newUser.email = email;
    		newUser.confirmationCode = UUID.randomUUID().toString();
    		newUser.status = UserStatus.CONFIRMATION_REQUIRED;
    		newUser.create();
    	}
    	Emails.confirm(newUser);
    	render(newUser);
    }

	public static void confirm(String confirmationCode){
		User newUser = checkConfirmationCode(confirmationCode);
    	render(newUser);
    }

    private static User checkConfirmationCode(String confirmationCode) {
		if(StringUtils.isEmpty(confirmationCode)){
			Validation.addError("confirmationCode", "Missing confirmation code");
			prepareForErrorRedirect();
			Application.index();
		}
    	User user = User.find("confirmationCode = ? AND status = ?", confirmationCode, UserStatus.CONFIRMATION_REQUIRED).first();
    	if(user == null){
    		Validation.addError("confirmationCode", "Invalid confirmation code");
    		prepareForErrorRedirect();
    		Application.index();
    	}
		return user;
	}

	public static void complete(String confirmationCode, 
    		String userName, 
    		String password, 
    		String password2, 
    		String firstName, 
    		String lastName) {

        User user = checkConfirmationCode(confirmationCode);
		validation.required(userName);
		validation.maxSize(userName, Util.USER_NAME_SIZE);
		User.validateUserName(userName);
		validation.required(password);
        validation.maxSize(password, Util.VARCHAR_SIZE);
		validation.required(password2);
        validation.maxSize(password2, Util.VARCHAR_SIZE);
		validation.required(firstName);
        validation.maxSize(firstName, Util.VARCHAR_SIZE);
		validation.required(lastName);
        validation.maxSize(lastName, Util.VARCHAR_SIZE);
        User.validatePasswordComplexity(password);
        
        if(validationFailed())
			confirm(confirmationCode);
		
        validation.equals(password, password2);
        
		if(User.findByUserName(userName) != null)
			Validation.addError("userName", "User name already taken");
		if(validationFailed())
			confirm(confirmationCode);
		
    	user.userName = userName;
    	// salt is not used anymore since we moved to BCrypt
    	user.changePassword(password);
    	user.firstName = firstName;
    	user.lastName = lastName;
    	user.confirmationCode = null;
    	user.status = UserStatus.REGISTERED;
    	user.save();
    	
    	Util.logSecurityAction("New user: %s", user.userName);
    	
    	login(user);
    	render(user);
    }

	private static void login(User user) {
	    session.clear();
        session.put("username", user.userName);
        response.setCookie("rememberme", Crypto.sign(user.userName) + "-" + user.userName, "30d");
	}
}