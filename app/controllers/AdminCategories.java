package controllers;

import java.util.List;

import models.Category;
import models.Module;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import util.Util;

@Check("admin")
public class AdminCategories extends LoggedInController {
	
	public static void index() {
		List<Category> categories = Category.allCategories();
		render(categories);
	}
	
	public static void addForm() {
		render();
	}
	
	public static void add(@Required @MaxSize(Util.VARCHAR_SIZE) String name,
			@MaxSize(Util.TEXT_SIZE) String description) {
		if (validationFailed()) {
			addForm();
		}
		
		Category category = Category.findByName(name);
		if(category != null){
			Validation.addError("name", "A category with the same name already exists !");
		}
		if(validationFailed()) {
			addForm();
		}
		
		category = new Category();
		category.name = name;
		category.description = description;
		category.save();
		
		flash("message", "The category '" + category.name + "' has been created.");
		index();
	}

	public static void editForm(Long id) {
		notFoundIfNull(id);
		Category category = Category.findById(id);
		notFoundIfNull(category);
		render(category);
	}
	
	public static void edit(@Required Long id,
			@Required @MaxSize(Util.VARCHAR_SIZE) String name,
			@MaxSize(Util.TEXT_SIZE) String description) {
		notFoundIfNull(id);
		
        Category category = Category.findByName(name);
		if(category != null) {
			if (!category.id.equals(id)) {
				Validation.addError("name", "A category with the same name already exists !");
			}
		}
		else {
			category = Category.findById(id);
			notFoundIfNull(category);
		}
		
		if(validationFailed()) {
			editForm(id);
		}
		
		category.name = name;
		category.description = description;
		category.save();

		flash("message", "The category '" + category.name + "' has been updated.");
		index();
	}
	
	public static void confirmDelete(Long id) {
		notFoundIfNull(id);
		Category category = Category.findById(id);
		notFoundIfNull(category);
		
		render(category);
	}
	
	public static void delete(Long id) {
		notFoundIfNull(id);
		Category category = Category.findById(id);
		notFoundIfNull(category);
		
		// remove the links
		int modules = category.modules.size();
		for(Module mod : category.modules){
		    mod.category = null;
		    mod.save();
		}
		
		category.delete();

		String message = "The category '" + category.name + "' has been removed.";
		if(modules == 1)
		    message += " 1 module has been updated.";
		else if(modules > 1)
            message += " " + modules + " modules have been updated.";
		
		flash("message", message);
		index();
	}
	
}
