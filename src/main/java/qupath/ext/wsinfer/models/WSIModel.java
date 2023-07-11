package qupath.ext.wsinfer.models;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
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

    static class ModelConfiguration {
        String spec_version;
        String architecture;
        int num_classes;
        List<String> class_names;
        int patch_size_pixels;
        float spacing_um_px;
        List<Transform> transform;

        float getTileSizeMicrons() {
            return patch_size_pixels * spacing_um_px;
        }
    }

    static class Transform {
        String name;
        Map<String, Object> arguments;
    }

    public void runModel() {
        Map<String, Object> pluginArgs = new HashMap<>();
        pluginArgs.put("tileSizeMicrons", configuration.getTileSizeMicrons());
        pluginArgs.put("trimToRoi", false);
        pluginArgs.put("makeAnnotation", false);
        pluginArgs.put("removeParentAnnotation", false);
        try {
            QP.runPlugin("qupath.lib.algorithms.TilerPlugin", pluginArgs);
        } catch (InterruptedException e) {
            logger.warn("Tiling interrupted", e);
        }

        ImageClassificationTranslator.Builder builder = ImageClassificationTranslator.builder().optSynset(configuration.class_names).optApplySoftmax(true);
        for (Transform transform: configuration.transform) {
            switch(transform.name) {
                case "Resize":
                    logger.info("Adding resize");
                    int size = ((Double) transform.arguments.get("size")).intValue();
                    builder.addTransform(new Resize(size));
                    break;
                case "ToTensor":
                    logger.info("Adding ToTensor");
                    builder.addTransform(new ToTensor());
                    break;
                case "Normalize":
                    logger.info("Adding Normalize");
                    ArrayList<Double> mean = (ArrayList<Double>) transform.arguments.get("mean");
                    ArrayList<Double> sd = (ArrayList<Double>) transform.arguments.get("std");
                    float[] meanArr = new float[mean.size()];
                    float[] sdArr = new float[mean.size()];
                    for (int i = 0; i < mean.size(); i++) {
                        meanArr[i] = mean.get(i).floatValue();
                        sdArr[i] = sd.get(i).floatValue();
                    }
                    builder.addTransform(new Normalize(meanArr, sdArr));
                    break;
                default:
                    logger.warn("Unknown transform name {}", transform.name);
                    break;
            }
        }
        Translator translator = builder.build();
        String device = WSInferPrefs.deviceProperty().get();
        Device dev = Device.cpu();
        switch (device) {
            case "gpu":
                dev = Device.gpu();
                break;
            case "cpu":
                dev = Device.cpu();
                break;
            default:
                logger.error("Unknown device, falling back to CPU");
        }
        Criteria criteria = Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optModelPath(getTSFile().toPath())
                .optEngine("PyTorch")
                .optTranslator(translator)
                .setTypes(Image.class, Classifications.class)
                .optDevice(dev)
                .build();
        long startTime = System.currentTimeMillis();
        try (ZooModel<Image, Classifications> model = criteria.loadModel()) {
            ImageData<BufferedImage> imageData = QP.getCurrentImageData();
            ImageServer<BufferedImage> server = imageData.getServer();
            PathObjectHierarchy hierarchy = imageData.getHierarchy();

            var tiles = hierarchy
                    .getSelectionModel()
                    .getSelectedObjects()
                    .stream()
                    .filter(PathObject::isTile).
                    collect(Collectors.toList());
            if (tiles.size() == 0) {
                tiles = (List<PathObject>) hierarchy.getTileObjects();
                logger.info("No tiles selected, so I'll try all of them");
            }
            int total = tiles.size();
            logger.info("{} tiles in total", total);
            Queue<PathObject> tileQueue = new LinkedList<>(tiles);

            double downsample = configuration.spacing_um_px / (double)server.getPixelCalibration().getAveragedPixelSize();
            int width = (int) Math.round(configuration.patch_size_pixels * downsample);
            int height = (int) Math.round(configuration.patch_size_pixels * downsample);
            AtomicInteger countdown = new AtomicInteger(total);

            int nThreads = 4;
            var pool = Executors.newFixedThreadPool(nThreads);
            for (int i = 0; i < nThreads; i++) {
                var worker = new PredictionWorker(
                        tileQueue,
                        8,
                        model,
                        server,
                        downsample,
                        width,
                        height,
                        configuration.class_names,
                        total,
                        countdown
                );
                pool.submit(worker);
            }
            pool.shutdown();
            // FIXME: does this mean terminate after 8 hours?
            // We can probably relax this, right? Less time until termination.
            pool.awaitTermination(8, TimeUnit.HOURS);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            logger.info("Finished {} tiles in {} seconds ({} ms per tile)", total, duration/1000, duration/total);
        } catch (InterruptedException | IOException | ModelNotFoundException | MalformedModelException e) {
            logger.error("Error running model {}", getName(), e);
        }
    }

    private File getTSFile() {
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

    class PredictionWorker implements Runnable {

        final Queue<PathObject> pathObjects;
        final int maxBatchSize;
        final ZooModel<Image, Classifications> model;

        final ImageServer<BufferedImage> server;
        final double downsample;
        final int width;
        final int height;
        final List<String> classNames;

        final int total;
        final AtomicInteger countdown;

        PredictionWorker(
            Queue<PathObject> pathObjects,
            int maxBatchSize,
            ZooModel<Image, Classifications> model,
            ImageServer<BufferedImage> server,
            double downsample,
            int width,
            int height,
            List<String> classNames,
            int total,
            AtomicInteger countdown
        ) {
            this.pathObjects = pathObjects;
            this.maxBatchSize = maxBatchSize;
            this.model = model;
            this.server = server;
            this.downsample = downsample;
            this.width = width;
            this.height = height;
            this.classNames = classNames;
            this.total = total;
            this.countdown = countdown;
        }

        @Override
        public void run() {
            try (Predictor<Image, Classifications> predictor = model.newPredictor()) {

                List<Image> inputs = new ArrayList<>();
                List<PathObject> toProcess = new ArrayList<>();
                while (!pathObjects.isEmpty()) {
                    inputs.clear();
                    toProcess.clear();
                    for (int i = 0; i < maxBatchSize; i++) {
                        PathObject pathObject = pathObjects.poll();
                        if (pathObject == null) {
                            break;
                        }
                        int count = total - countdown.decrementAndGet();
                        if (total % 100 == 0) {
                            logger.info("Processing {}/{}", count, total);
                        }
                        toProcess.add(pathObject);
                        ROI roi = pathObject.getROI();
                        int x = (int) Math.round(roi.getCentroidX() - width / 2.0);
                        int y = (int) Math.round(roi.getCentroidY() - height / 2.0);
                        BufferedImage img = server.readRegion(downsample, x, y, width, height);
                        Image input = BufferedImageFactory.getInstance().fromImage(img);
                        inputs.add(input);
                    }

                    if (inputs.isEmpty()) {
                        return;
                    }
                    List<Classifications> predictions = predictor.batchPredict(inputs);
                    for (int i = 0; i < inputs.size(); i++) {
                        Classifications classifications = predictions.get(i);
                        for (String c : classNames) {
                            double prob = classifications.get(c).getProbability();
                            toProcess.get(i).getMeasurements().put(c, prob);
                            // TODO: we can consider thresholding here.
                        }
                    }
                }

            } catch (Exception e) {
                logger.error(e.getLocalizedMessage(), e);
            }
        }
    }
}
