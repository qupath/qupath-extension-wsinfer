package qupath.ext.wsinfer.models;
import java.util.Collections;
import java.util.Map;

// Defines a map where the top level element is "models", which is itself a map of "name" -> WSIModel.
public class ModelCollection {
    private Map<String, WSIModel> models;

    public Map<String, WSIModel> getModels() {
        return Collections.unmodifiableMap(models);
    }
}
