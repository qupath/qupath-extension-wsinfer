package qupath.ext.wsinfer.models;
import java.util.Collections;
import java.util.Map;

//Class to store the fetched json from hugging-face with no parsing
public class ModelCollection {
    private Map<String, WSIModel> models;

    public Map<String, WSIModel> getModels() {
        return Collections.unmodifiableMap(models);
    }
}
