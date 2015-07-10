package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import play.db.jpa.Model;

@Entity
@SuppressWarnings("serial")
@Table(name = "herd_metainf")
public class HerdMetainf extends Model {

	@Column(nullable = false, unique = true)
	public String key;

    @Column
    public String value;

    public static HerdMetainf findByKey(String key) {
        return find("lower(name) = lower(?)", key).first();
    }
    
}