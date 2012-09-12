package util;

import java.util.Comparator;

import models.ModuleVersion;

public class VersionComparator implements Comparator<ModuleVersion>{

    @Override
    public int compare(ModuleVersion a, ModuleVersion b) {
        return a.compareTo(b);
    }

}
