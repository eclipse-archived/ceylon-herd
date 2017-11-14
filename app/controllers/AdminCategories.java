/********************************************************************************
 * Copyright (c) {date} Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
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
        List<Category> categories = Category.findAllCategories();
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
        Category category = getCategory(id);

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
            category = getCategory(id);
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
        Category category = getCategory(id);

        render(category);
    }

    public static void delete(Long id) {
        Category category = getCategory(id);

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

    private static Category getCategory(Long id) {
        notFoundIfNull(id);
        Category category = Category.findById(id);
        notFoundIfNull(category);
        return category;
    }
}
