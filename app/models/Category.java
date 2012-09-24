package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
public class Category extends Model {

	@Column(nullable = false, unique = true)
	public String name;

    // Hibernate would map @Lob to a CLOB instead of TEXT
    @Column(columnDefinition = "TEXT")
	public String description;
    
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    public List<Module> modules = new ArrayList<Module>();

    //
    // Static helpers
    
    public static List<Category> allCategories() {
        return find("ORDER BY name").fetch();
    }

    public static Category findByName(String name) {
        return find("lower(name) = lower(?)", name).first();
    }
}
