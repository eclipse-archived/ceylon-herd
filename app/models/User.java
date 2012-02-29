package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.Table;

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
	
	public static User connect(String username, String password) {
		return find("userName = ? AND password = ?", username, Codec.hexSHA1(password)).first();
	}
}
