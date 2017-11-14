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

public enum CeylonElementType {
    Package,
    Module,
    Function,
    Value,
    Object,
    Annotation,
    AnnotationConstructor,
    Class,
    Interface;
    
    public int typeWeight() {
        switch(this){
        case Module:
            return 0;
        case Package:
            return 1;
        case Annotation:
        case AnnotationConstructor:
            return 2;
        case Object:
        case Value:
        case Function:
            return 3;
        case Class:
        case Interface:
            return 4;
        }
        return 0;
    }

}
