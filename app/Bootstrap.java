/********************************************************************************
 * Copyright (c) {date} Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
import java.io.File;
import java.util.EnumSet;

import models.Author;
import models.Dependency;
import models.HerdMetainf;
import models.Module;
import models.ModuleVersion;
import models.User;
import models.UserStatus;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import util.CeylonElementType;
import util.Util;
 
@OnApplicationStart
public class Bootstrap extends Job {
 
    private static final String ASL = "Apache Software License";
    private static final String ASL2 = "Apache Software License 2.0";

    public void doJob() {
       
    	File repo = Util.getRepoDir();
    	if (!repo.exists()) {
    		repo.mkdir();
    	}
    	
    	File uploads = Util.getUploadDir();
    	if (!uploads.exists()) {
    		uploads.mkdir();
    	}
    	
    	if (Play.mode.isDev()) {
    	    HerdMetainf.initialize();
    	    if(Play.runingInTestMode()){
    	        loadTestDb();
    	    }
    	}
    	
    }

    private void loadTestDb() {
        User owner = new User();
        owner.email = "stef@epardaud.fr";
        owner.firstName = "Stef";
        owner.lastName = "Epardaud";
        owner.isBCrypt = true;
        owner.userName = "FroMage";
        owner.status = UserStatus.REGISTERED;
        owner.create();
        
        Module frEpardaudTest = createModule("fr.epardaud.test", owner);
        Module frEpardaudTest2 = createModule("fr.epardaud.test2", owner);
        Module frEpardaudJava = createModule("fr.epardaud.java", owner);
        Module ceylonCollection = createModule("ceylon.collection", owner);
        
        Author stef = createAuthor("Stéphane Épardaud");
        Author tom = createAuthor("Tom Bentley");
        Author enrique = createAuthor("Enrique Zamudio");

        ModuleVersion ceylonCollection666 = newModuleVersion(ceylonCollection, "6.6.6", ASL2, "Collection documentation", stef, tom, enrique);
        setArtifacts(ceylonCollection666, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 8, 9);
        ceylonCollection666.create();

        // fr.epardaud.test
        
        ModuleVersion frEpardaudTest12 = newModuleVersion(frEpardaudTest, "1.2", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest12, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 8, 9);
        frEpardaudTest12.create();
        addDependency(frEpardaudTest12, ceylonCollection666, false, true);

        ModuleVersion frEpardaudTest13 = newModuleVersion(frEpardaudTest, "1.3", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest13, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 7, 7);
        frEpardaudTest13.create();

        ModuleVersion frEpardaudTest14 = newModuleVersion(frEpardaudTest, "1.4", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest14, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 8, 8);
        frEpardaudTest14.create();

        ModuleVersion frEpardaudTest15 = newModuleVersion(frEpardaudTest, "1.5", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest15, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 9, 8);
        frEpardaudTest15.create();

        ModuleVersion frEpardaudTest16 = newModuleVersion(frEpardaudTest, "1.6", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest16, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 9, 9);
        frEpardaudTest16.create();

        ModuleVersion frEpardaudTest17 = newModuleVersion(frEpardaudTest, "1.7", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest17, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 10, 10);
        frEpardaudTest17.create();

        ModuleVersion frEpardaudTest23 = newModuleVersion(frEpardaudTest, "2.3", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest23, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 8, 9);
        frEpardaudTest23.create();
        addDependency(frEpardaudTest23, ceylonCollection666, false, true);
        frEpardaudTest23.addMember("fr.epardaud.test", "ParseException", CeylonElementType.Class, true);

        // fr.epardaud.test2

        ModuleVersion frEpardaudTest2_10 = newModuleVersion(frEpardaudTest2, "1.0", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest2_10, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 8, 9);
        frEpardaudTest2_10.create();
        addDependency(frEpardaudTest2_10, ceylonCollection666, false, true);

        ModuleVersion frEpardaudTest2_12 = newModuleVersion(frEpardaudTest2, "1.2", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest2_12, EnumSet.of(Artifact.CAR, Artifact.JS, Artifact.SRC), 8, 9);
        frEpardaudTest2_12.create();
        addDependency(frEpardaudTest2_12, ceylonCollection666, false, true);
        frEpardaudTest2_12.addMember("fr.epardaud.test2", "ParseException", CeylonElementType.Class, true);
        frEpardaudTest2_12.addMember("fr.epardaud.test2.float", "pi", CeylonElementType.Value, true);

        // only JVM
        ModuleVersion frEpardaudTest03 = newModuleVersion(frEpardaudTest2, "0.3", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest03, EnumSet.of(Artifact.CAR, Artifact.SRC), 8, 0);
        frEpardaudTest03.create();
        addDependency(frEpardaudTest03, ceylonCollection666, false, true);

        // only JS
        ModuleVersion frEpardaudTest04 = newModuleVersion(frEpardaudTest2, "0.4", ASL, "Super documentation", stef, tom);
        setArtifacts(frEpardaudTest04, EnumSet.of(Artifact.JS, Artifact.SRC), 0, 9);
        frEpardaudTest04.create();
        addDependency(frEpardaudTest04, ceylonCollection666, false, true);

        // fr.epardaud.java
        
        ModuleVersion frEpardaudJava1 = newModuleVersion(frEpardaudJava, "1", null, null);
        setArtifacts(frEpardaudJava1, EnumSet.of(Artifact.JAR), 0, 0);
        frEpardaudJava1.create();
    }

    private enum Artifact {
        CAR, JAR, JS, SRC; 
    }
    
    private void setArtifacts(ModuleVersion mv, EnumSet<Artifact> artifacts, 
            int jvmBinaryMajor, int jsBinaryMajor) {
        mv.isCarPresent = artifacts.contains(Artifact.CAR);
        mv.isJsPresent = artifacts.contains(Artifact.JS);
        mv.isJarPresent = artifacts.contains(Artifact.JAR);
        mv.isSourcePresent = artifacts.contains(Artifact.SRC);
        mv.jvmBinMajor = jvmBinaryMajor;
        mv.jsBinMajor = jsBinaryMajor;
    }

    private void addDependency(ModuleVersion owner, ModuleVersion dependency, boolean optional, boolean shared) {
        Dependency dep = new Dependency(owner, dependency.module.name, dependency.version, 
                optional, shared, false, false, false, false);
        dep.create();
    }

    private ModuleVersion newModuleVersion(Module module, String version, String license, String doc, Author... authors) {
        ModuleVersion mv = new ModuleVersion();
        mv.module = module;
        mv.version = version;
        mv.license = license;
        mv.doc = doc;
        for(Author author : authors)
            mv.authors.add(author);
        return mv;
    }

    private Author createAuthor(String name) {
        Author author = new Author();
        author.name = name;
        author.create();
        return author;
    }

    private Module createModule(String name, User owner) {
        Module module = new Module();
        module.name = name;
        module.owner = owner;
        module.create();
        return module;
    }
 
}