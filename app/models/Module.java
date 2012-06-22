package models;

import org.apache.commons.lang.StringUtils;
import play.db.jpa.Model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@SuppressWarnings("serial")
public class Module extends Model {

	public static final Pattern githubPattern = Pattern.compile("https?://github.com/([^/]+)/([^/]+)/?");

	@Column(nullable = false, unique = true)
	public String name;
	
	public String friendlyName;
	public String homeURL;
	public String issueTrackerURL;
	public String codeURL;
	
	@JoinColumn(nullable = false)
	@ManyToOne
	public User owner;
	
	@OneToMany(mappedBy = "module")
	public List<ModuleVersion> versions = new ArrayList<ModuleVersion>();

	@ManyToMany
    @JoinTable(name = "module_admin_user",
            	joinColumns = { @JoinColumn(name = "module") },
            	inverseJoinColumns = { @JoinColumn(name = "admin") })
	public List<User> admins = new ArrayList<User>();

	@Transient
	public boolean isGithub(){
		if(StringUtils.isEmpty(codeURL))
			return false;
		Matcher matcher = githubPattern.matcher(codeURL);
		return matcher.matches();
	}

	@Transient
	public String getGithubOwner(){
		if(StringUtils.isEmpty(codeURL))
			return null;
		Matcher matcher = githubPattern.matcher(codeURL);
		if(!matcher.matches())
			return null;
		return matcher.group(1);
	}

	@Transient
	public String getGithubProject(){
		if(StringUtils.isEmpty(codeURL))
			return null;
		Matcher matcher = githubPattern.matcher(codeURL);
		if(!matcher.matches())
			return null;
		return matcher.group(2);
	}

	@Transient
	public Date getLastPublished(){
		return find("SELECT MAX(published) FROM ModuleVersion WHERE module = ?", this).first(); 
	}
	
    @Transient
    public long getDownloads(){
        long ret = 0;
        for(ModuleVersion version : versions){
            ret += version.downloads;
        }
        return ret;
    }

    @Transient
    public long getJsDownloads(){
        long ret = 0;
        for(ModuleVersion version : versions){
            ret += version.jsdownloads;
        }
        return ret;
    }

	@Transient
	public long getSourceDownloads(){
	    long ret = 0;
	    for(ModuleVersion version : versions){
	        ret += version.sourceDownloads;
	    }
	    return ret;
	}

    @Transient
    public String getPath(){
        return name.replace('.', '/');
    }

    public boolean canEdit(User user){
		return user != null
				&& (user.equals(owner)
						|| user.isAdmin
						|| admins.contains(user));
	}
	
	public boolean isOwnedBy(User user){
		return user != null
				&& (user.equals(owner)
						|| user.isAdmin);
	}

	//
	// Static helpers
	
	public static Module findByName(String moduleName) {
		return find("name = ?", moduleName).first();
	}

    public static List<Module> searchByName(String q) {
        return find("LOCATE(?, name) <> 0", q).fetch();
    }

	public static List<Module> findByOwner(User owner) {
		return find("owner = ? ORDER BY name", owner).fetch();
	}

	public static long countForOwner(User owner) {
		return count("owner = ?", owner);
	}
}
