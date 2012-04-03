package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import play.cache.Cache;
import play.db.jpa.Model;
import play.libs.Codec;
import play.mvc.Scope.Session;
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

	public static User connect(String username, String password) {
		return find("userName = ? AND password = ?", username, Codec.hexSHA1(password)).first();
	}

	public static User findRegisteredByUserName(String username) {
		return find("LOWER(userName) = ? AND status = ?", username.toLowerCase(), UserStatus.REGISTERED).first();
	}

	public static User findByUserName(String username) {
		return find("LOWER(userName) = ?", username.toLowerCase()).first();
	}
}
