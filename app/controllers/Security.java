package controllers;

import static controllers.MyController.prepareForErrorRedirect;
import static controllers.MyController.validationFailed;

import java.util.Date;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;

import models.User;
import models.UserStatus;
import notifiers.Emails;
import play.data.validation.Email;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.Codec;
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
            return User.find("byUserName", connected()).<User> first().isAdmin;
        }
        return false;
    }

    static void onDisconnected() {
        Application.index();
    }
    
    public static void resetPasswordRequest() {
        render();
    }
    
    public static void resetPasswordRequest2(@Required String username, @Required @Email String email) {
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
        
        Util.logSecurityAction("Reset password request: %s", user.userName);
        
        Emails.resetPassword(user);
        
        render(email);
    }
    
    public static void resetPasswordComplete(String confirmationCode) {
        User user = checkConfirmationCodeAndGetUser(confirmationCode);
        render(user);
    }

    public static void resetPasswordComplete2(String confirmationCode, String password, String password2) {
        User user = checkConfirmationCodeAndGetUser(confirmationCode);

        validation.required(password);
        validation.maxSize(password, Util.VARCHAR_SIZE);
        validation.required(password2);
        validation.maxSize(password2, Util.VARCHAR_SIZE);
        validation.equals("password", password, "password confirmation", password2);
        if (validationFailed()) {
            resetPasswordComplete(confirmationCode);
        }

        user.password = Codec.hexSHA1(user.salt + password);
        user.passwordResetConfirmationCode = null;
        user.passwordResetConfirmationDate = null;
        user.save();

        Util.logSecurityAction("Reset password: %s", user.userName);

        session.put("username", user.userName);
        response.setCookie("rememberme", Crypto.sign(user.userName) + "-" + user.userName, "30d");

        flash("message", "New password set successfully.");
        Application.index();
    }

    private static User checkConfirmationCodeAndGetUser(String confirmationCode) {
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