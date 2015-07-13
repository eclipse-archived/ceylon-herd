package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

// TODO:  Figure out what needs to go in here, exactly
@Entity
@SuppressWarnings("serial")
public class HerdDependency extends Model {

    public String name;
	public String version;

	@ManyToOne
	public Upload upload;

    public HerdDependency(String name, String version, Upload upload) {
        this.name = name;
        this.version = version;
        this.upload = upload;
    }
}
