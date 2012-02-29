package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@SuppressWarnings("serial")
@Entity
public class Comment extends Model {
	public Date date;
	@ManyToOne
	public User owner;
	@ManyToOne
	public Project project;

	@Lob
	public String text;
	
	@Enumerated(EnumType.STRING)
	public ProjectStatus status;
}
