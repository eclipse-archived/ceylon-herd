package models;

import org.apache.commons.lang.StringUtils;
import play.db.jpa.Model;
import play.libs.Codec;
import util.MyCache;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

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
	
	@OneToMany(mappedBy = "owner")
	public List<Project> projects = new ArrayList<Project>();
	@OneToMany(mappedBy = "owner")
	public List<Upload> uploads = new ArrayList<Upload>();

	@Column(unique = true)
	public String confirmationCode;
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	public UserStatus status;

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

	public static User connect(String username, String password) {
	    // sanity check
	    if(StringUtils.isEmpty(username)
	            || StringUtils.isEmpty(password))
	        return null;
	    // find the user
	    User nonVerifiedUser = find("userName = ?", username).first();
	    // doesn't exist?
	    if(nonVerifiedUser == null)
	        return null;
	    // check for invalid users
	    if(StringUtils.isEmpty(nonVerifiedUser.password)
	            || StringUtils.isEmpty(nonVerifiedUser.salt))
	        return null;
	    // now check the password
	    if(Codec.hexSHA1(nonVerifiedUser.salt+password).equals(nonVerifiedUser.password))
	        // all good!
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
}
