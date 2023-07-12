package qupath.ext.wsinfer.models;
import java.util.Collections;
import java.util.Map;

// Defines a map where the top level element is "models", which is itself a map of "name" -> WSIModel.
public class WSInferModelCollection {
    private Map<String, WSInferModel> models;

    public Map<String, WSInferModel> getModels() {
        return Collections.unmodifiableMap(models);
    }
}
