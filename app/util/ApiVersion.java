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
