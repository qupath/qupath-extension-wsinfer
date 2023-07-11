package qupath.ext.wsinfer.models;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.BufferedImageFactory;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


// Equivalent to config.json files from hugging face
public class WSIModel {

    private final Logger logger = LoggerFactory.getLogger(WSIModel.class);

    private String description;
    private ModelConfiguration configuration;

    @SerializedName("hf_repo_id")
    private String hfRepoId;

    @SerializedName("hf_revision")
    private String hfRevision;

    public String getName() {
        return hfRepoId;
    }

    public ModelConfiguration getConfiguration() {
        return configuration;
    }

    public static class ModelConfiguration {
        String spec_version;
        String architecture;
        int num_classes;
        List<String> class_names;
        int patch_size_pixels;
        float spacing_um_px;
        List<Transform> transform;

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

    public static class Transform {
        String name;
        Map<String, Object> arguments;

        public String getName() {
            return name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }
    }

    public File getTSFile() {
        String ts = "torchscript_model.pt";
        String dir = WSInferPrefs.modelDirectoryProperty().get();
        String modPath = String.format("%s" + File.separator + "%s" + File.separator + "/%s", dir, hfRepoId, hfRevision);
        return new File(String.format("%s/%s", modPath, ts));
    }

    public void fetchModel() {
        String ts = "torchscript_model.pt";
        String cf = "config.json";
        URL tsURL;
        URL cfURL;
        try {
            tsURL = new URL(String.format("https://huggingface.co/%s/resolve/%s/%s", hfRepoId, hfRevision, ts));
            cfURL = new URL(String.format("https://huggingface.co/%s/resolve/%s/%s", hfRepoId, hfRevision, cf));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        String dir = WSInferPrefs.modelDirectoryProperty().get();
        String modPath = String.format("%s" + File.separator + "%s" + File.separator + "/%s", dir, hfRepoId, hfRevision);
        File modDir = new File(modPath);
        if (!modDir.exists()) {
            try {
                Files.createDirectories(modDir.toPath());
            } catch (IOException e) {
                logger.error("Cannot create directory for model files {}", modDir, e);
            }
        }
        File tsFile = new File(String.format("%s/%s", modPath, ts));
        if (!tsFile.exists()) {
            WSInferUtils.downloadURLToFile(tsURL, tsFile);
        }
        File cfFile = new File(String.format("%s/%s", modPath, cf));
        if (!cfFile.exists()) {
            WSInferUtils.downloadURLToFile(cfURL, cfFile);
        }
        String json = null;
        try {
            json = Files.readString(cfFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Cannot read file {}", cfFile, e);
        }
        this.configuration = GsonTools.getInstance().fromJson(json, ModelConfiguration.class);
    }
}
