package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Validation;
import play.db.jpa.Model;
import play.libs.Codec;
import util.BCrypt;
import util.MyCache;

@SuppressWarnings("serial")
@Entity
@Table(name = "user_table")
public class User extends Model {
	
	@Column(nullable = false)
	public String email;
	@Column(unique = true)
	public String userName;
	public String password;
	public String salt;
	public String firstName;
	public String lastName;
	@Column(name = "admin")
	public boolean isAdmin;
    public boolean isBCrypt;
	
	@OneToMany(mappedBy = "owner")
	@OrderBy("moduleName")
	public List<Project> projects = new ArrayList<Project>();
	@OneToMany(mappedBy = "owner")
	public List<Upload> uploads = new ArrayList<Upload>();

	@Column(unique = true)
	public String confirmationCode;
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	public UserStatus status;
	
	@Column(unique = true)
	public String passwordResetConfirmationCode;
	public Date passwordResetConfirmationDate;

	@Transient
	public boolean isRegistered(){
	    return status == UserStatus.REGISTERED;
	}
	
	@Transient
	public List<Project> getOwnedProjects(){
	    return Project.findForOwner(this);
	}

	@Transient
	public List<ModuleVersion> getLastPublishedModuleVersions(){
		return ModuleVersion.latestForOwner(this, 20);
	}

	@Transient
	public long getPublishedModules(){
		return ModuleVersion.countForOwner(this);
	}

	@Transient
	public long getProjectsCached(){
	    return MyCache.getProjectsForOwner(this);
	}

	@Transient
	public long getUploadsCached(){
	    return MyCache.getUploadsForOwner(this);
	}

	@Transient
	public long getModulesCached(){
		return MyCache.getModulesForOwner(this);
	}

    @Transient
    public boolean isEmailConfirmationNeeded() {
        return StringUtils.contains(confirmationCode, "|") && StringUtils.contains(confirmationCode, "@");
    }

    @Transient
    public String getEmailToConfirm() {
        String emailToConfirm = null;
        if (isEmailConfirmationNeeded()) {
            emailToConfirm = confirmationCode.substring(confirmationCode.indexOf("|") + 1);
        }
        return emailToConfirm;
    }

    public boolean checkPassword(String password) {
        if(isBCrypt){
            return BCrypt.checkpw(password, this.password);
        }else{
            return Codec.hexSHA1(this.salt+password).equals(this.password);
        }
    }

    public void changePassword(String newPassword) {
        this.isBCrypt = true;
        this.password = BCrypt.hashpw(newPassword, BCrypt.gensalt());
    }

	public static User connect(String username, String password) {
	    // sanity check
	    if(StringUtils.isEmpty(username)
	            || StringUtils.isEmpty(password))
	        return null;
	    // find the user
	    User nonVerifiedUser = findRegisteredByUserName(username);
	    // doesn't exist?
	    if(nonVerifiedUser == null)
	        return null;
	    // check for invalid users
	    if(StringUtils.isEmpty(nonVerifiedUser.password)
	            // only nonBCrypt users need a salt
	            || (!nonVerifiedUser.isBCrypt && StringUtils.isEmpty(nonVerifiedUser.salt)))
	        return null;
        // now check the password
	    if(nonVerifiedUser.checkPassword(password))
	        return nonVerifiedUser;
	    // password fail!
		return null;
	}

    public static User findRegisteredByUserName(String username) {
        return find("LOWER(userName) = ? AND status = ?", username.toLowerCase(), UserStatus.REGISTERED).first();
    }

    public static User findByUserName(String username) {
        return find("LOWER(userName) = ?", username.toLowerCase()).first();
    }
    
    public static User findByUserNameAndConfirmationCode(String username, String confirmationCode) {
        return find("LOWER(userName) = ? and confirmationCode = ?", username.toLowerCase(), confirmationCode).first();
    }

    public static void validatePasswordComplexity(String password) {
        // size is already checked
        if(StringUtils.isEmpty(password))
            return;
        if(password.length() < 8
                || !hasLetterAndDigit(password))
            Validation.addError("password", "Passwords should be at least 8 characters long and contain at least one letter and one digit");
    }

    private static boolean hasLetterAndDigit(String str) {
        char[] charArray = str.toCharArray();
        boolean hasLetter = false, hasDigit = false;
        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];
            if((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z'))
                hasLetter = true;
            else if(c >= '0' && c <= '9')
                hasDigit = true;
            if(hasLetter && hasDigit)
                return true;
        }
        return false;
    }
}