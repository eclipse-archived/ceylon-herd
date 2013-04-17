package controllers;

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
import util.Util;

import java.util.UUID;

public class LoggedInUsers extends LoggedInController {

    public static final String EMAIL_SEPARATOR = "|";

    private static User getUser(String username) {
        if(username == null || username.isEmpty()) {
            Validation.addError("", "Unknown user");
            prepareForErrorRedirect();
            Application.index();
        }

        models.User user = models.User.findRegisteredByUserName(username);
        notFoundIfNull(user);
        if(!isAuthorised(user)){
            Validation.addError("", "Unauthorised");
            prepareForErrorRedirect();
            Users.view(username);
        }
        return user;
    }

    public static void editForm(String username){
        User editedUser = getUser(username);
        
        render(editedUser);
    }

    public static void edit(@Required String username,
            @MaxSize(Util.VARCHAR_SIZE) String firstName,
            @MaxSize(Util.VARCHAR_SIZE) String lastName,
            @Required @MaxSize(Util.VARCHAR_SIZE) @Email String email,
            boolean isAdmin){
        User currentUser = getUser();
        
        if(validationFailed()){
            editForm(username);
        }
        User editedUser = getUser(username);

        editedUser.firstName = firstName;
        editedUser.lastName = lastName;

        // Email update
        boolean hasToConfirmEmail = false;
        boolean skipEmail = "true".equals(Play.configuration.get("register.skip.email"));
        if (!StringUtils.equals(editedUser.email, email)) {
            // Only admin can modify mail without confirmation
            hasToConfirmEmail = !currentUser.isAdmin && !skipEmail;
            if (hasToConfirmEmail) {
                editedUser.confirmationCode = UUID.randomUUID().toString() + EMAIL_SEPARATOR + email;
            }
            else {
                editedUser.email = email;
            }
        }
        // In case the user has previously modify its email and not confirmed it,
        // we delete the confirmation code
        else if(editedUser.isEmailConfirmationNeeded()) {
            editedUser.confirmationCode = null;
        }

        // only support setting admin from admins
        if(currentUser.isAdmin){
            editedUser.isAdmin = isAdmin;
        }

        editedUser.save();

        if (hasToConfirmEmail) {
            flash("message", "User profile modified. An email has been sent to "
                    + email
                    + ". Please follow the instructions in this email in order to validate your new email address.");
            Emails.confirmEmailModification(editedUser, email);
        }
        else {
            flash("message", "User profile modified.");
        }
        Users.view(username);
    }

    public static void passwordForm(String username) {
        User editedUser = getUser(username);

        render(editedUser);
    }

    public static void passwordEdit(@Required String username,
            @MaxSize(Util.VARCHAR_SIZE) String oldPassword,
            @Required @MaxSize(Util.VARCHAR_SIZE) String password,
            @Required @MaxSize(Util.VARCHAR_SIZE) String password2) {
        User currentUser = getUser();
        
        // admin doesn't need old password but regular users do
        if(!currentUser.isAdmin){
            Validation.required("oldPassword", oldPassword);
        }
        
        if(validationFailed()){
            passwordForm(username);
        }
        User editedUser = getUser(username);

        // old password check for non-admins
        if(!currentUser.isAdmin){
            if(!Codec.hexSHA1(editedUser.salt + oldPassword).equals(editedUser.password)){
                Validation.addError("oldPassword", "Wrong Password");
                prepareForErrorRedirect();
                passwordForm(username);
            }
        }

        if(!password.equals(password2)) {
            Validation.addError("password2","Confirmation password doesn't match the new password");
            prepareForErrorRedirect();
            passwordForm(username);
        }

        if(password.equals(oldPassword)){
            Validation.addError("password", "Old and new password are the same!");
            prepareForErrorRedirect();
            passwordForm(username);
        }

        editedUser.password = Codec.hexSHA1(editedUser.salt + password);
        editedUser.save();

        flash("message", "Password modified.");
        Users.view(username);
    }


    public static void confirmEmail(String username, String confirmationCode){
        if(StringUtils.isEmpty(confirmationCode)) {
            Validation.addError("confirmationCode", "Missing confirmation code");
            prepareForErrorRedirect();
            Application.index();
        }
        User user = User.find("confirmationCode = ? and userName = ?", confirmationCode, username).first();
        if(user == null){
            Validation.addError("confirmationCode", "Invalid confirmation code");
            prepareForErrorRedirect();
            Application.index();
        }

        int separatorIndex = confirmationCode.indexOf(EMAIL_SEPARATOR);
        if (separatorIndex < 0) {
            Validation.addError("confirmationCode", "Invalid confirmation code");
            prepareForErrorRedirect();
            Application.index();
        }
        String email = confirmationCode.substring(separatorIndex + 1);

        if (!StringUtils.isEmpty(email)) {
            user.email = email;
            user.confirmationCode = null;
            user.save();
        }

        render(user);
    }

    private static boolean isAuthorised(User user){
        return getUser() == user || getUser().isAdmin;
    }
}
