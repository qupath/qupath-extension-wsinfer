package qupath.ext.wsinfer;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Transform;
import ai.djl.translate.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.WSInferModelConfiguration;
import qupath.ext.wsinfer.models.WSInferTransform;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main class to run inference with WSInfer.
 */
public class WSInfer {

    private static final Logger logger = LoggerFactory.getLogger(WSInfer.class);

    private static final String title = "WSInfer";

    public static void runInference(WSInferModel wsiModel) throws ModelNotFoundException, MalformedModelException, IOException, InterruptedException {
        runInference(QP.getCurrentImageData(), wsiModel);
    }

    public static void runInference(String modelName) throws ModelNotFoundException, MalformedModelException, IOException, InterruptedException {
        var model = loadModel(modelName);
        runInference(QP.getCurrentImageData(), model);
    }


    public static void runInference(ImageData<BufferedImage> imageData, String modelName) throws ModelNotFoundException, MalformedModelException, IOException, InterruptedException {
        var model = loadModel(modelName);
        runInference(imageData, model);
    }

    /**
     * Get a model from the model collection.
     * @param modelName the name of the model to fetch
     * @return the model; this should never be null, because an exception is thrown if the model is not found.
     * @throws IllegalArgumentException if no model is found with the given name.
     */
    private static WSInferModel loadModel(String modelName) throws IllegalArgumentException {
        var modelCollection = WSInferUtils.parseModels();
        var model = modelCollection.getModels().getOrDefault(modelName, null);
        if (model == null) {
            throw new IllegalArgumentException("No model found with name: " + modelName);
        }
        return model;
    }


    public static void runInference(ImageData<BufferedImage> imageData, WSInferModel wsiModel) throws InterruptedException, ModelNotFoundException, MalformedModelException, IOException {
        Objects.requireNonNull(wsiModel, "Model cannot be null");
        if (imageData == null) {
            Dialogs.showNoImageError(title);
        }

        // Try to get some tiles we can use
        var tiles = getTilesForInference(imageData, wsiModel.getConfiguration());
        if (tiles.isEmpty()) {
            logger.warn("No tiles to process!");
            return;
        }

        Device device = getDevice();

        Pipeline pipeline = new Pipeline();
        int resize = -1;
        for (WSInferTransform transform: wsiModel.getConfiguration().getTransform()) {
            switch(transform.getName()) {
                case "Resize":
                    // Ideally we'd resize with the pipeline, but unfortunately that fails with MPS devides -
                    // so instead we need to resize first
//                    int size = ((Double) transform.getArguments().get("size")).intValue();
//                    builder.addTransform(new Resize(size, size, Image.Interpolation.BILINEAR));
                    resize = ((Number)transform.getArguments().get("size")).intValue();
                    logger.debug("Requesting resize to {}", resize);
                    break;
                case "ToTensor":
                    pipeline.add(createToTensorTransform(device));
                    break;
                case "Normalize":
                    pipeline.add(createNormalizeTransform(transform));
                    break;
                default:
                    logger.warn("Ignoring unknown transform: {}", transform.getName());
                    break;
            }
        }

        boolean applySoftmax = true;
        Translator translator = buildTranslator(wsiModel, pipeline, applySoftmax);
        Criteria<Image, Classifications> criteria = buildCriteria(wsiModel, translator, device);

        long startTime = System.currentTimeMillis();
        try (ZooModel<Image, Classifications> model = criteria.loadModel()) {

            int nTiles = tiles.size();
            logger.info("Running {} for {} tiles", wsiModel.getName(), nTiles);

            Queue<PathObject> tileQueue = new LinkedBlockingQueue<>(tiles);

            ImageServer<BufferedImage> server = imageData.getServer();
            double downsample = wsiModel.getConfiguration().getSpacingMicronPerPixel() / (double)server.getPixelCalibration().getAveragedPixelSize();
            int width = (int) Math.round(wsiModel.getConfiguration().getPatchSizePixels() * downsample);
            int height = (int) Math.round(wsiModel.getConfiguration().getPatchSizePixels() * downsample);

            var workerBuilder = WSInferWorker.builder()
                    .batchSize(1)
                    .server(server)
                    .tileSize(width, height)
                    .downsample(downsample)
                    .tileQueue(tileQueue)
                    .model(model)
                    .classNames(wsiModel.getConfiguration().getClassNames())
                    .resizeTile(resize, resize);

            // Don't want to use lots of threads, because inference is expected to either be multithreadered
            // or limited by the GPU. But we don't want to use one thread, in case IO is the bottleneck.
            int nThreads = Math.min(4, Runtime.getRuntime().availableProcessors());
            ExecutorService pool = Executors.newFixedThreadPool(nThreads);

            for (int i = 0; i < nThreads; i++) {
                WSInferWorker worker = workerBuilder.build();
                pool.submit(worker);
            }
            pool.shutdown();
            // FIXME: does this mean terminate after 8 hours?
            // We can probably relax this, right? Less time until termination.
            pool.awaitTermination(8, TimeUnit.HOURS);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            imageData.getHierarchy().fireObjectClassificationsChangedEvent(WSInfer.class, tiles);
            logger.info("Finished {} tiles in {} seconds ({} ms per tile)", nTiles, duration/1000, duration/nTiles);
        } catch (InterruptedException e) {
            logger.error("Model inference interrupted {}", wsiModel.getName(), e);
            throw e;
        } catch (IOException | ModelNotFoundException | MalformedModelException e) {
            logger.error("Error running model {}", wsiModel.getName(), e);
            throw e;
        }
    }


    private static Translator<Image, Classifications> buildTranslator(WSInferModel wsiModel, Pipeline pipeline, boolean applySoftmax) {
        // We should use ImageClassificationTranslator.builder() in the future if this is updated to work with MPS
        // (See javadocs for MpsSupport.WSInferClassificationTranslator for details)
        //        ImageClassificationTranslator.Builder builder = ImageClassificationTranslator.builder()
        return MpsSupport.WSInferClassificationTranslator.builder()
                .optSynset(wsiModel.getConfiguration().getClassNames())
                .optApplySoftmax(applySoftmax)
                .setPipeline(pipeline)
                .build();
    }

    private static Criteria<Image, Classifications> buildCriteria(WSInferModel wsiModel, Translator<Image, Classifications> translator, Device device) {
        return Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optModelPath(wsiModel.getTSFile().toPath())
                .optEngine("PyTorch")
                .setTypes(Image.class, Classifications.class)
                .optTranslator(translator)
                .optDevice(device)
                .build();
    }


    private static Device getDevice() {
        String deviceName = WSInferPrefs.deviceProperty().get();
        Device device;
        switch (deviceName) {
            case "gpu":
                return Device.gpu();
            case "cpu":
                return Device.cpu();
            default:
                logger.info("Attempting to set device to {}", deviceName);
                return Device.fromName(deviceName);
        }
    }


    private static Transform createToTensorTransform(Device device) {
        logger.debug("Creating ToTensor transform");
        if (device != null && device.getDeviceType().toLowerCase().startsWith("mps"))
            return new MpsSupport.ToTensor32();
        else
            return new ToTensor();
    }

    private static Transform createNormalizeTransform(WSInferTransform transform) {
        ArrayList<Double> mean = (ArrayList<Double>) transform.getArguments().get("mean");
        ArrayList<Double> sd = (ArrayList<Double>) transform.getArguments().get("std");
        logger.debug("Creating Normalize transform (mean={}, sd={})", mean, sd);
        float[] meanArr = new float[mean.size()];
        float[] sdArr = new float[mean.size()];
        for (int i = 0; i < mean.size(); i++) {
            meanArr[i] = mean.get(i).floatValue();
            sdArr[i] = sd.get(i).floatValue();
        }
        return new Normalize(meanArr, sdArr);
    }


    private static List<PathObject> getTilesForInference(ImageData<BufferedImage> imageData, WSInferModelConfiguration config) {
        // Here, we permit detections to be used instead of tiles
        var selectedObjects = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
        var selectedTiles = selectedObjects.stream()
                .filter(p -> p.isTile() || p.isDetection())
                .collect(Collectors.toList());
        if (!selectedTiles.isEmpty()) {
            return selectedTiles;
        }

        // If we have annotations selected, create tiles inside them
        Collection<PathObject> selectedAnnotations = selectedObjects.stream()
                .filter(p -> p.isAnnotation())
                .collect(Collectors.toList());
        if (!selectedAnnotations.isEmpty()) {
            var annotationSet = new LinkedHashSet<>(selectedAnnotations); // We want this later
            Map<String, Object> pluginArgs = new LinkedHashMap<>();
            if (imageData.getServer().getPixelCalibration().hasPixelSizeMicrons()) {
                pluginArgs.put("tileSizeMicrons", config.getPatchSizePixels() * config.getSpacingMicronPerPixel());
            } else {
                logger.warn("Pixel calibration not available, so using pixels instead of microns");
                pluginArgs.put("tileSizePixels", config.getPatchSizePixels());
            }
            pluginArgs.put("trimToROI", false);
            pluginArgs.put("makeAnnotations", false);
            pluginArgs.put("removeParentAnnotation", false);
            try {
                QP.runPlugin("qupath.lib.algorithms.TilerPlugin", imageData, pluginArgs);
                // We want our new tiles to be selected... but we also want to ensure that any tile object
                // has a selected annotation as a parent (in case there were other tiles already)
                return imageData.getHierarchy().getTileObjects()
                        .stream()
                        .filter(t -> annotationSet.contains(t.getParent()))
                        .collect(Collectors.toList());
            } catch (InterruptedException e) {
                logger.warn("Tiling interrupted", e);
            }
        }
        throw new IllegalArgumentException("No tiles or annotations selected!");
    }


}