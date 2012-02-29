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

import play.db.jpa.Model;
import play.libs.Codec;

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
		return Project.find("owner = ? AND status = ?", this, ProjectStatus.CONFIRMED).fetch();
	}

	@Transient
	public List<ModuleVersion> getLastPublishedModuleVersions(){
		return ModuleVersion.find("module.owner = ? ORDER BY published DESC", this).fetch(20);
	}

	@Transient
	public long getPublishedModules(){
		return ModuleVersion.count("module.owner = ?", this);
	}

	public static User connect(String username, String password) {
		return find("userName = ? AND password = ?", username, Codec.hexSHA1(password)).first();
	}

	public static User findByUserName(String username) {
		return find("userName = ? AND status = ?", username, UserStatus.REGISTERED).first();
	}
}
