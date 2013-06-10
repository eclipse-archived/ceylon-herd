package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class MavenDependency extends Model {

    public String name;
	public String version;

	@ManyToOne
	public Upload upload;

    public MavenDependency(String name, String version, Upload upload) {
        this.name = name;
        this.version = version;
        this.upload = upload;
    }
}
