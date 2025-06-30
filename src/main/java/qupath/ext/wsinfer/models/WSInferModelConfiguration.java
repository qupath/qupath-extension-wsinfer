/**
 * Copyright 2023 University of Edinburgh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qupath.ext.wsinfer.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Class representing the configuration of a WSInfer model.
 * <p>
 * This is a Java representation of the JSON configuration file,
 * and stores the key information needed to run the model (preprocessing, resolution, output classes).
 * <p>
 * See https://github.com/SBU-BMI/wsinfer-zoo/blob/main/wsinfer_zoo/schemas/model-config.schema.json
 */
public class WSInferModelConfiguration {

    @SerializedName("spec_version")
    private String specVersion;

    private String architecture;

    @SerializedName("num_classes")
    private int numClasses;
    private List<String> class_names;

    @SerializedName("patch_size_pixels")
    private int patchSizePixels;

    @SerializedName("spacing_um_px")
    private double spacingUmPx;

    @SerializedName("apply_softmax")
    private boolean applySoftmax = true;

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
        return patchSizePixels;
    }

    /**
     * Requested pixel size in microns.
     * @return
     */
    public double getSpacingMicronPerPixel() {
        return spacingUmPx;
    }

    /**
     * Whether to apply softmax to model output
     * @return
     */
    public boolean isApplySoftmax() {
        return applySoftmax;
    }
}
