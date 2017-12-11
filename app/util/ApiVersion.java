/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package util;

public enum ApiVersion {
    API1("1"),
    API2("2"),
    API3("3"),
    API4("4"),
    API5("5");
    
    public final String version;

    private ApiVersion(String version){
        this.version = version;
    }
}
