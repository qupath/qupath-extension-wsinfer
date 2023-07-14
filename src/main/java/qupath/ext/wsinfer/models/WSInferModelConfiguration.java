package qupath.ext.wsinfer.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Class representing the configuration of a WSInfer model.
 * <p>
 * This is a Java representation of the JSON configuration file,
 * and stored the key information needed to run the model (preprocessing, resolution, output classes).
 * <p>
 * See https://github.com/SBU-BMI/wsinfer-zoo/blob/main/wsinfer_zoo/schemas/model-config.schema.json
 */
public class WSInferModelConfiguration {

    @SerializedName("spec_version")
    private String spec_version;

    private String architecture;

    @SerializedName("num_classes")
    private int num_classes;
    private List<String> class_names;

    @SerializedName("patch_size_pixels")
    private int patch_size_pixels;

    @SerializedName("spacing_um_px")
    private double spacing_um_px;

    private List<WSInferTransform> transform;

    /**
     * Output classification names.
     * @return
     */
    public List<String> getClassNames() {
        return class_names;
    }

    /**
     * Transform for preprocessing input patches.
     * @return
     */
    public List<WSInferTransform> getTransform() {
        return transform;
    }

    /**
     * Classification patch size in pixels
     * @return
     */
    public double getPatchSizePixels() {
        return patch_size_pixels;
    }

    /**
     * Requested pixel size in microns.
     * @return
     */
    public double getSpacingMicronPerPixel() {
        return spacing_um_px;
    }
}
