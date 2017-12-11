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

import java.util.Comparator;

import models.ModuleVersion;

public class VersionComparator implements Comparator<ModuleVersion>{

    @Override
    public int compare(ModuleVersion a, ModuleVersion b) {
        return a.compareTo(b);
    }

}
