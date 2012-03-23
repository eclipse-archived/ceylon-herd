package controllers;

import java.util.UUID;

import models.User;
import models.UserStatus;
import notifiers.Emails;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Required;
import play.data.validation.Validation;
import play.libs.Codec;
import play.libs.Crypto;

public class Register extends MyController {

    public static void index() {
        render();
    }

    public static void register(@Required String email) {
        badRequest();
        
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
		validation.required(password);
		validation.required(password2);
		validation.required(firstName);
		validation.required(lastName);
		if(validationFailed())
			confirm(confirmationCode);
		validation.equals(password, password2);
		if(User.findByUserName(userName) != null)
			Validation.addError("userName", "User name already taken");
		if(validationFailed())
			confirm(confirmationCode);
		
    	user.userName = userName;
    	user.password = Codec.hexSHA1(password);
    	user.firstName = firstName;
    	user.lastName = lastName;
    	user.confirmationCode = null;
    	user.status = UserStatus.REGISTERED;
    	user.save();
    	login(user);
    	render(user);
    }

	private static void login(User user) {
        session.put("username", user.userName);
        response.setCookie("rememberme", Crypto.sign(user.userName) + "-" + user.userName, "30d");
	}
}