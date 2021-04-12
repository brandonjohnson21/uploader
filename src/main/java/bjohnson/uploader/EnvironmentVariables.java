package bjohnson.uploader;

import java.util.Map;

public class EnvironmentVariables {
    private static EnvironmentVariables _instance = new EnvironmentVariables();
    private EnvironmentVariables() {

    }
    public static EnvironmentVariables getInstance() {
        return _instance;
    }
    public String get(String variable) {
        return System.getenv(variable);
    }
    public Map<String,String> get() {
        return System.getenv();
    }
}
