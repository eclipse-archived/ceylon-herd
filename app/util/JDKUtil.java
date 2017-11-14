/********************************************************************************
 * Copyright (c) {date} Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package util;

import java.util.HashSet;
import java.util.Set;

import play.vfs.VirtualFile;

public class JDKUtil {
    
    private static final Set<String> jdkModules = new HashSet<String>(); 
    
    static {
        VirtualFile file = VirtualFile.open("conf/jdk-modules.conf");
        if(file == null || !file.exists())
            throw new RuntimeException("Can't read jdk-modules.conf");
        String[] lines = file.contentAsString().split("\n");
        for(String line : lines){
            line = line.trim();
            if(!line.isEmpty())
                jdkModules.add(line);
        }
    }
    
    public static boolean isJdkModule(String module){
        return jdkModules.contains(module);
    }
}
