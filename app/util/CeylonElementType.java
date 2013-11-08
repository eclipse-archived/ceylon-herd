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
