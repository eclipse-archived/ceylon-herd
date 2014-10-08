package models;

import java.util.Comparator;

public class ModuleScriptComparator implements Comparator<ModuleScript> {

    @Override
    public int compare(ModuleScript a, ModuleScript b) {
        return a.compareTo(b);
    }

}
