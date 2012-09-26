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
import play.libs.Codec;
import play.libs.Crypto;
import play.libs.Mail.Mock;
import util.Util;

public class Register extends MyController {

    public static void index() {
        render();
    }

    public static void register(@Required @MaxSize(Util.VARCHAR_SIZE) @Email String email) {
        //badRequest();
        
    	if(validationFailed())
    		index();
    	User user = User.find("email = ? AND status = ?", email, UserStatus.CONFIRMATION_REQUIRED).first();
    	if(user == null){
    		user = new User();
    		user.email = email;
    		user.confirmationCode = UUID.randomUUID().toString();
    		user.status = UserStatus.CONFIRMATION_REQUIRED;
    		user.create();
    	}
    	Emails.confirm(user);
    	render(user);
    }

	public static void confirm(String confirmationCode){
		User user = checkConfirmationCode(confirmationCode);
    	render(user);
    }

    private static User checkConfirmationCode(String confirmationCode) {
		if(StringUtils.isEmpty(confirmationCode)){
			Validation.addError(confirmationCode, "Missing confirmation code");
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
		validation.maxSize(userName, Util.VARCHAR_SIZE);
		validation.required(password);
        validation.maxSize(password, Util.VARCHAR_SIZE);
		validation.required(password2);
        validation.maxSize(password2, Util.VARCHAR_SIZE);
		validation.required(firstName);
        validation.maxSize(firstName, Util.VARCHAR_SIZE);
		validation.required(lastName);
        validation.maxSize(lastName, Util.VARCHAR_SIZE);
        
        if(validationFailed())
			confirm(confirmationCode);
		
        validation.equals(password, password2);
        
		if(User.findByUserName(userName) != null)
			Validation.addError("userName", "User name already taken");
		if(validationFailed())
			confirm(confirmationCode);
		
    	user.userName = userName;
    	user.salt = UUID.randomUUID().toString();
    	user.password = Codec.hexSHA1(user.salt+password);
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
        session.put("username", user.userName);
        response.setCookie("rememberme", Crypto.sign(user.userName) + "-" + user.userName, "30d");
	}
}