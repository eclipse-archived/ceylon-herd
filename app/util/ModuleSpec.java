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

import play.Logger;

public class ModuleSpec{
    public String name, version;

    public ModuleSpec(String name, String version) {
        this.name = name;
        this.version = version;
    }

    @SuppressWarnings("serial")
    public static class ModuleSpecException extends Exception{
        public ModuleSpecException(String message) {
            super(message);
        }
    }
    
    public static ModuleSpec parse(String name, String postfix) throws ModuleSpecException{
        int sep = name.indexOf('-');
        if(sep == -1){
            if(name.equals("default"+postfix))
                throw new ModuleSpecException("Default module not allowed.");
            else
                throw new ModuleSpecException("Module car has no version: "+name);
        }
        int dot = name.lastIndexOf('.');
        String module = name.substring(0, sep);
        if(module.isEmpty()){
            throw new ModuleSpecException("Empty module name not allowed: "+name);
        }
        if(module.equals("default")){
            throw new ModuleSpecException("Default module not allowed: "+name);
        }
        String version = name.substring(sep+1, dot);
        if(version.isEmpty()){
            throw new ModuleSpecException("Empty version number not allowed: "+name);
        }
        Logger.info("module: %s", module);
        return new ModuleSpec(module, version);
    }
}