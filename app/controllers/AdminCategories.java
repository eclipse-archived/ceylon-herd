package controllers;

import java.util.List;

import models.Category;
import models.User;

import org.junit.experimental.categories.Categories;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import util.Util;

public class AdminCategories extends LoggedInController {

	
	public static void index() {
		List<Category> categories = Category.find("ORDER BY name").fetch();
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
		
		Category category = Category.find("name = ?", name).first();
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
		
		Category category = Category.find("name = ?", name).first();
		if(category != null) {
			if (category.id != id) {
				Validation.addError("name", "A category with the same name already exists !");
			}
		}
		else {
			category = Category.findById(id);
			notFoundIfNull(id);
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
		
		category.delete();

		flash("message", "The category '" + category.name + "' has been removed.");
		index();
	}
	
}
