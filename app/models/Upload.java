package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import play.db.jpa.Model;
import util.JavaExtensions;
import util.Util;

@SuppressWarnings("serial")
@Entity
public class Upload extends Model {
	@ManyToOne
	public User owner;
	public Date created;
	
	@Transient
	public long getSize(){
		return JavaExtensions.folderSize(Util.getUploadDir(id));
	}

	@Transient
	public long getFileCount(){
		return JavaExtensions.countFiles(Util.getUploadDir(id));
	}

	//
	// Static helpers
	
    public static Long countForOwner(User owner) {
        return count("owner = ?", owner);
    }
}
