package qupath.ext.wsinfer;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Transform;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.ext.wsinfer.models.WSInferModelConfiguration;
import qupath.ext.wsinfer.models.WSInferTransform;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.ext.wsinfer.ui.TileCreator;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main class to run inference with WSInfer.
 */
public class WSInfer {

    private static final Logger logger = LoggerFactory.getLogger(WSInfer.class);
    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");

    private static final String title = "WSInfer";

    /**
     * Run inference on the current image data using the given model.
     * @param wsiModel
     * @throws ModelNotFoundException
     * @throws MalformedModelException
     * @throws IOException
     * @throws InterruptedException
     * @throws TranslateException
     */
    public static void runInference(WSInferModel wsiModel) throws ModelNotFoundException, MalformedModelException, IOException, InterruptedException, TranslateException {
        runInference(QP.getCurrentImageData(), wsiModel);
    }

    /**
     * Run inference on the current image data using the specified model.
     * @param modelName name of the model to use for inference
     * @throws ModelNotFoundException
     * @throws MalformedModelException
     * @throws IOException
     * @throws InterruptedException
     * @throws TranslateException
     */
    public static void runInference(String modelName) throws ModelNotFoundException, MalformedModelException, IOException, InterruptedException, TranslateException {
        var model = loadModel(modelName);
        runInference(QP.getCurrentImageData(), model);
    }

    /**
     * Run inference on the specified image data using the specified model.
     * @param imageData image data to run inference on
     * @param modelName name of the model to use for inference
     * @throws ModelNotFoundException
     * @throws MalformedModelException
     * @throws IOException
     * @throws InterruptedException
     * @throws TranslateException
     */
    public static void runInference(ImageData<BufferedImage> imageData, String modelName) throws ModelNotFoundException, MalformedModelException, IOException, InterruptedException, TranslateException {
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
        Objects.requireNonNull(modelName, "Model name cannot be null");
        var modelCollection = WSInferUtils.getModelCollection();
        var model = modelCollection.getModels().getOrDefault(modelName, null);
        if (model == null && modelName.contains("/")) {
            String shortModelName = modelName.substring(modelName.lastIndexOf("/") + 1);
            model = modelCollection.getModels().getOrDefault(shortModelName, null);
            if (model != null)
                logger.warn("Using short model name {} instead of {}", shortModelName, modelName);
        }
        if (model == null) {
            throw new IllegalArgumentException("No model found with name: " + modelName);
        }
        return model;
    }

    /**
     * Run inference on the specified image data using the given model.
     * @param imageData image data to run inference on
     * @param wsiModel model to use for inference
     * @throws InterruptedException
     * @throws ModelNotFoundException
     * @throws MalformedModelException
     * @throws IOException
     * @throws TranslateException
     */
    public static void runInference(ImageData<BufferedImage> imageData, WSInferModel wsiModel) throws InterruptedException, ModelNotFoundException, MalformedModelException, IOException, TranslateException {
        runInference(imageData, wsiModel, new ProgressLogger(logger));
    }

    /**
     * Run inference on the specified image data using the given model with a custom progress listener.
     * @param imageData image data to run inference on (required)
     * @param wsiModel model to use for inference (required)
     * @param progressListener the progress listener to report what is happening (required)
     * @throws InterruptedException
     * @throws ModelNotFoundException
     * @throws MalformedModelException
     * @throws IOException
     * @throws TranslateException
     */
    public static void runInference(ImageData<BufferedImage> imageData, WSInferModel wsiModel, ProgressListener progressListener) throws InterruptedException, ModelNotFoundException, MalformedModelException, IOException, TranslateException {
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
        List<String> classNames = wsiModel.getConfiguration().getClassNames();

        long startTime = System.currentTimeMillis();
        try (ZooModel<Image, Classifications> model = criteria.loadModel()) {
            int nTiles = tiles.size();
            logger.info("Running {} for {} tiles", wsiModel.getName(), nTiles);

            ImageServer<BufferedImage> server = imageData.getServer();
            double downsample = wsiModel.getConfiguration().getSpacingMicronPerPixel() / (double)server.getPixelCalibration().getAveragedPixelSize();
            int width = (int) Math.round(wsiModel.getConfiguration().getPatchSizePixels() * downsample);
            int height = (int) Math.round(wsiModel.getConfiguration().getPatchSizePixels() * downsample);

            // Number of workers who will be busy fetching tiles for us while we're busy inferring
            int nWorkers = Math.max(1, WSInferPrefs.numWorkersProperty().getValue());

            // Make a guess at a batch size currently... *must* be 1 for MPS
            // FIXME: Make batch size adjustable when using a GPU (or CPU?)
            int batchSize = isMPS(device) || !device.isGpu() ? 1 : 4;
            if (device == Device.cpu())
                batchSize = 4;

            var tileLoader = TileLoader.builder()
                    .batchSize(batchSize)
                    .numWorkers(nWorkers)
                    .server(server)
                    .tileSize(width, height)
                    .downsample(downsample)
                    .tiles(tiles)
                    .resizeTile(resize, resize)
                    .build();

            double completedTiles = 0;
            double totalTiles = tiles.size();
            progressListener.updateProgress(
                    String.format(resources.getString("ui.processing-progress"), Math.round(completedTiles), Math.round(totalTiles)),
                    completedTiles/totalTiles);

            try (Predictor<Image, Classifications> predictor = model.newPredictor()) {
                var batchQueue = tileLoader.getBatchQueue();
                int pendingWorkers = nWorkers;
                while (pendingWorkers > 0 && !Thread.currentThread().isInterrupted()) {
                    var batch = batchQueue.take();
                    if (batch.isEmpty()) {
                        // We stop when all the tile workers have returned an empty batch
                        pendingWorkers--;
                        continue;
                    }

                    List<Image> inputs = batch.getInputs();
                    List<PathObject> pathObjectBatch = batch.getTiles();
                    List<Classifications> predictions = predictor.batchPredict(inputs);

                    for (int i = 0; i < inputs.size(); i++) {
                        PathObject pathObject = pathObjectBatch.get(i);
                        Classifications classifications = predictions.get(i);
                        for (String c : classNames) {
                            double prob = classifications.get(c).getProbability();
                            pathObject.getMeasurements().put(c, prob);
                        }
                        // Set class based upon probability
                        String name = classifications.topK(1).get(0).getClassName();
                        if (name == null)
                            pathObject.resetPathClass();
                        else
                            pathObject.setPathClass(PathClass.fromString(name));
                    }
                    completedTiles += inputs.size();
                    progressListener.updateProgress(
                            String.format(resources.getString("ui.processing-progress"), Math.round(completedTiles), Math.round(totalTiles)),
                            completedTiles/totalTiles);
                }
            }
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            progressListener.updateProgress(
                    String.format(resources.getString("ui.processing-completed"), Math.round(completedTiles), Math.round(totalTiles)),
                    1.0);

            imageData.getHierarchy().fireObjectClassificationsChangedEvent(WSInfer.class, tiles);
            long durationSeconds = duration/1000;
            String seconds = durationSeconds == 1 ? "second" : "seconds";
            logger.info("Finished {} tiles in {} {} ({} ms per tile)", nTiles, durationSeconds, seconds, duration/nTiles);
        } catch (InterruptedException e) {
            logger.error("Model inference interrupted {}", wsiModel.getName(), e);
            progressListener.updateProgress("Inference interrupted!", 1.0);
            throw e;
        } catch (IOException | ModelNotFoundException | MalformedModelException | TranslateException e) {
            logger.error("Error running model {}", wsiModel.getName(), e);
            progressListener.updateProgress("Inference failed!", 1.0);
            throw e;
        }
    }

    /**
     * Check if a specified device corresponds to using the Metal Performance Shaders (MPS) backend (Apple Silicon)
     * @param device
     * @return
     */
    private static boolean isMPS(Device device) {
        return device != null && device.getDeviceType().toLowerCase().startsWith("mps");
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
        if (isMPS(device))
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
            double tileSizePx = 0, tileSizeMicrons = 0;
            if (imageData.getServer().getPixelCalibration().hasPixelSizeMicrons()) {
                tileSizeMicrons = config.getPatchSizePixels() * config.getSpacingMicronPerPixel();
            } else {
                logger.warn("Pixel calibration not available, so using pixels instead of microns");
                tileSizePx = config.getPatchSizePixels();
            }
            for (var annotation: selectedAnnotations) {
                TileCreator tileCreator = new TileCreator(imageData,
                        annotation, tileSizeMicrons, tileSizePx,
                        false, false, false, true);
                tileCreator.run();
            }

            // We want our new tiles to be selected... but we also want to ensure that any tile object
            // has a selected annotation as a parent (in case there were other tiles already)
            return imageData.getHierarchy().getTileObjects()
                    .stream()
                    .filter(t -> annotationSet.contains(t.getParent()))
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException("No tiles or annotations selected!");
    }


}
