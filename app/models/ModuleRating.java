package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class ModuleRating extends Model {

	@ManyToOne
	public Module module;
	
	@ManyToOne
	public User owner;
	
	public int mark;
	
}
