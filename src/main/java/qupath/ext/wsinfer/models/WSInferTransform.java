package qupath.ext.wsinfer.models;

import java.util.Map;

/**
 * A single preprocessing transform to use with a WSInfer model.
 */
public class WSInferTransform {

    private String name;
    private Map<String, Object> arguments;

    /**
     * Name of the transform.
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Optional arguments associated with the transform.
     * @return
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }
}
