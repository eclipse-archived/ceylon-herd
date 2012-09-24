package models;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@SuppressWarnings("serial")
@Entity
public class ModuleComment extends Model {
	
	public Date date;
	@ManyToOne
	public User owner;
	@ManyToOne
	public Module module;

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String text;
    
}
