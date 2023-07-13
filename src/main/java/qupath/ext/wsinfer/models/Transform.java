package qupath.ext.wsinfer.models;

import java.util.Map;

public class Transform {

    private String name;
    private Map<String, Object> arguments;

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}
