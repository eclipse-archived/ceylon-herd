package models;

import javax.persistence.Column;
import javax.persistence.Entity;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class Author extends Model {

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String name;

	//
	// Static helpers
	
	public static Author findOrCreate(String name) {
		Author author = find("name = ?", name).first();
		if(author == null){
		    author = new Author();
		    author.name = name;
		    author.create();
		}
		return author;
	}
}
