package models;

import javax.persistence.Column;
import javax.persistence.Entity;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class Category extends Model {

	@Column(nullable = false, unique = true)
	public String name;

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String description;
    
}
