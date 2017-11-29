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

import static controllers.MyController.prepareForErrorRedirect;
import static controllers.MyController.validationFailed;

import java.util.Date;
import java.util.UUID;

import models.User;
import notifiers.Emails;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;

import play.data.validation.Email;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.Crypto;
import util.Util;

public class Security extends Secure.Security {
    
    static boolean authenticate(String username, String password) {
        if (User.connect(username, password) != null)
            return true;
        Util.logSecurityAction("failed login for %s", username);
        return false;
    }

    static boolean check(String profile) {
        if ("admin".equals(profile)) {
            User user = getCurrentUser();
            if (user != null) {
                return user.isAdmin;
            }
        }
        return false;
    }

    private static User getCurrentUser() {
        User user = null;
        String username = connected();
        if (StringUtils.isNotEmpty(username)) {
            user = (User) renderArgs.get("user");
            if (user == null || !username.equalsIgnoreCase(user.userName)) {
                user = User.findRegisteredByUserName(username);
            }
        }
        return user;
    }

    static void onDisconnected() {
        Application.index();
    }
    
    public static void resetPasswordRequest() {
        if(getCurrentUser() != null){
            flash("warning", "Cannot reset password while logged in.");
            Application.index();
        }
        render();
    }
    
    public static void resetPasswordRequest2(@Required String username, @Required @Email String email) {
        if(getCurrentUser() != null){
            flash("warning", "Cannot reset password while logged in.");
            Application.index();
        }
        Util.logSecurityAction("Reset password request: username=%s, email=%s", username, email);
        
        if (validationFailed()) {
            resetPasswordRequest();
        }
        
        User user = User.findRegisteredByUserName(username);
        if (user == null || !user.email.equalsIgnoreCase(email)) {
            Validation.addError("unknownUser", "​There is no registered user with these name and email address.");
            prepareForErrorRedirect();
            resetPasswordRequest();
        }
        
        user.passwordResetConfirmationCode = UUID.randomUUID().toString();
        user.passwordResetConfirmationDate = new Date();
        user.save();
        
        Emails.resetPassword(user);
        
        render(email);
    }
    
    public static void resetPasswordComplete(String confirmationCode) {
        if(getCurrentUser() != null){
            flash("warning", "Cannot reset password while logged in.");
            Application.index();
        }
        User user = checkConfirmationCodeAndGetUser(confirmationCode);
        render(user);
    }

    public static void resetPasswordComplete2(String confirmationCode, String password, String password2) {
        if(getCurrentUser() != null){
            flash("warning", "Cannot reset password while logged in.");
            Application.index();
        }
        User user = checkConfirmationCodeAndGetUser(confirmationCode);

        validation.required(password);
        validation.maxSize(password, Util.VARCHAR_SIZE);
        validation.required(password2);
        validation.maxSize(password2, Util.VARCHAR_SIZE);
        Validation.equals("password", password, "password confirmation", password2);
        User.validatePasswordComplexity(password);
        if (validationFailed()) {
            resetPasswordComplete(confirmationCode);
        }

        user.changePassword(password);
        user.passwordResetConfirmationCode = null;
        user.passwordResetConfirmationDate = null;
        user.save();

        Util.logSecurityAction("Reset password successful: %s", user.userName);

        session.put("username", user.userName);
        response.setCookie("rememberme", Crypto.sign(user.userName) + "-" + user.userName, "30d");

        flash("message", "New password set successfully.");
        Application.index();
    }

    private static User checkConfirmationCodeAndGetUser(String confirmationCode) {
        Util.logSecurityAction("Reset password attempt: %s", confirmationCode);
        
        if (StringUtils.isEmpty(confirmationCode)) {
            Validation.addError("confirmationCode", "Can't reset password, missing confirmation code.");
            prepareForErrorRedirect();
            resetPasswordRequest();
        }

        User user = User.find("passwordResetConfirmationCode = ?", confirmationCode).first();
        if (user == null) {
            Validation.addError("confirmationCode", "Can't reset password, invalid confirmation code.");
            prepareForErrorRedirect();
            resetPasswordRequest();
        }

        if (user.passwordResetConfirmationDate == null || DateUtils.addDays(user.passwordResetConfirmationDate, 1).before(new Date())) {
            Validation.addError("confirmationCode", "Can't reset password, expired confirmation code.");
            prepareForErrorRedirect();
            resetPasswordRequest();
        }

        return user;
    }
    
}