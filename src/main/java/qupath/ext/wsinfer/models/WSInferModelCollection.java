package qupath.ext.wsinfer.models;

import java.util.Collections;
import java.util.Map;

/**
 * Wrapper for a collection of WSInfer models.
 *
 * See https://github.com/SBU-BMI/wsinfer-zoo/blob/main/wsinfer_zoo/schemas/wsinfer-zoo-registry.schema.json
 */
public class WSInferModelCollection {

    private Map<String, WSInferModel> models;

    /**
     * Get a map of model names to models.
     * @return
     */
    public Map<String, WSInferModel> getModels() {
        return Collections.unmodifiableMap(models);
    }
}
