package qupath.ext.wsinfer.models;

import java.util.List;

public class ModelConfiguration {

    private String spec_version;
    private String architecture;
    private int num_classes;
    private List<String> class_names;
    private int patch_size_pixels;
    private float spacing_um_px;
    private List<Transform> transform;

    public float getTileSizeMicrons() {
        return patch_size_pixels * spacing_um_px;
    }

    public List<String> getClassNames() {
        return class_names;
    }

    public List<Transform> getTransform() {
        return transform;
    }

    public float getPatchSizePixels() {
        return patch_size_pixels;
    }

    public float getSpacingMicronPerPixel() {
        return spacing_um_px;
    }
}
