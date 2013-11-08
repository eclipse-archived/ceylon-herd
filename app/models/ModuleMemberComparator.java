package models;

import java.util.Comparator;

public class ModuleMemberComparator implements Comparator<ModuleMember> {

    @Override
    public int compare(ModuleMember a, ModuleMember b) {
        return a.compareTo(b);
    }

}
