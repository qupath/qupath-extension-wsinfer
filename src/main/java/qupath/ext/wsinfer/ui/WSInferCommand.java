package qupath.ext.wsinfer.ui;

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
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class WSInferCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WSInferController.class);

    private final QuPathGUI qupath;

    private final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");

    private Stage stage;

    public WSInferCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (stage == null) {
            try {
                stage = createStage();
            } catch (IOException e) {
                Dialogs.showErrorMessage(resources.getString("title"),
                        resources.getString("error.initializing"));
                logger.error(e.getMessage(), e);
                return;
            }
        }
        stage.show();
    }

    private Stage createStage() throws IOException {

        URL url = getClass().getResource("wsinfer_control.fxml");
        if (url == null) {
            throw new IOException("Cannot find URL for WSInfer FXML");
        }

        Parent root = FXMLLoader.load(url, resources);

        Scene scene = new Scene(root);

        Stage stage = new Stage();
        stage.initOwner(qupath.getStage());
        stage.setTitle(resources.getString("title"));
        stage.setScene(scene);

        return stage;
    }

    public static void runInference(WSInferModel wsiModel) {
        Map<String, Object> pluginArgs = new HashMap<>();
        pluginArgs.put("tileSizeMicrons", wsiModel.getConfiguration().getTileSizeMicrons());
        pluginArgs.put("trimToROI", false);
        pluginArgs.put("makeAnnotations", false);
        pluginArgs.put("removeParentAnnotation", false);
        try {
            QP.runPlugin("qupath.lib.algorithms.TilerPlugin", pluginArgs);
        } catch (InterruptedException e) {
            logger.warn("Tiling interrupted", e);
        }

        ImageClassificationTranslator.Builder builder = ImageClassificationTranslator.builder()
                .optSynset(wsiModel.getConfiguration().getClassNames())
                .optApplySoftmax(true);

        for (WSInferModel.Transform transform: wsiModel.getConfiguration().getTransform()) {
            switch(transform.getName()) {
                case "Resize":
                    logger.info("Adding resize");
                    int size = ((Double) transform.getArguments().get("size")).intValue();
                    builder.addTransform(new Resize(size));
                    break;
                case "ToTensor":
                    logger.info("Adding ToTensor");
                    builder.addTransform(new ToTensor());
                    break;
                case "Normalize":
                    logger.info("Adding Normalize");
                    ArrayList<Double> mean = (ArrayList<Double>) transform.getArguments().get("mean");
                    ArrayList<Double> sd = (ArrayList<Double>) transform.getArguments().get("std");
                    float[] meanArr = new float[mean.size()];
                    float[] sdArr = new float[mean.size()];
                    for (int i = 0; i < mean.size(); i++) {
                        meanArr[i] = mean.get(i).floatValue();
                        sdArr[i] = sd.get(i).floatValue();
                    }
                    builder.addTransform(new Normalize(meanArr, sdArr));
                    break;
                default:
                    logger.warn("Unknown transform name {}", transform.getName());
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
                logger.info("Unknown device, falling back to CPU");
        }

        Criteria criteria = Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optModelPath(wsiModel.getTSFile().toPath())
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

            Collection<PathObject> tiles = hierarchy
                    .getSelectionModel()
                    .getSelectedObjects()
                    .stream()
                    .filter(PathObject::isTile).
                    collect(Collectors.toList());

            if (tiles.size() == 0) {
                tiles = hierarchy.getTileObjects();
                logger.info("No tiles selected, so I'll try all of them");
            }
            int total = tiles.size();
            logger.info("{} tiles in total", total);
            Dialogs.showPlainNotification("WSInfer plugin", "Running " + wsiModel.getName() + " on " + total + " tiles");
            Queue<PathObject> tileQueue = new LinkedList<>(tiles);

            double downsample = wsiModel.getConfiguration().getSpacingMicronPerPixel() / (double)server.getPixelCalibration().getAveragedPixelSize();
            int width = (int) Math.round(wsiModel.getConfiguration().getPatchSizePixels() * downsample);
            int height = (int) Math.round(wsiModel.getConfiguration().getPatchSizePixels() * downsample);
            AtomicInteger countdown = new AtomicInteger(total);

            int nThreads = 4;
            ExecutorService pool = Executors.newFixedThreadPool(nThreads);

            for (int i = 0; i < nThreads; i++) {
                PredictionWorker worker = new PredictionWorker(
                        tileQueue,
                        8,
                        model,
                        server,
                        downsample,
                        width,
                        height,
                        wsiModel.getConfiguration().getClassNames(),
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
            logger.error("Error running model {}", wsiModel.getName(), e);
        }
        Dialogs.showPlainNotification("WSInfer plugin", "Finished running " + wsiModel.getName());
    }

    static class PredictionWorker implements Runnable {

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
                        if (count % 100 == 0) {
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
