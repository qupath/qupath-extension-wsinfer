package qupath.ext.wsinfer;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.BufferedImageFactory;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.ZooModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

class WSInferWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WSInferWorker.class);

    private final Queue<? extends PathObject> pathObjects;
    private final int maxBatchSize;
    private final ZooModel<Image, Classifications> model;

    private final ImageServer<BufferedImage> server;
    private final double downsample;
    private final int width;
    private final int height;
    private final List<String> classNames;

    private final int resizeWidth;
    private final int resizeHeight;

    private final int total;
    private final AtomicInteger countdown;

    private WSInferWorker(Builder builder) {
        this.pathObjects = builder.pathObjects;
        this.maxBatchSize = builder.batchSize;
        this.model = builder.model;
        this.server = builder.server;
        this.downsample = builder.downsample;
        this.width = builder.width;
        this.height = builder.height;
        this.resizeWidth = builder.resizeWidth;
        this.resizeHeight = builder.resizeHeight;
        this.classNames = new ArrayList<>(builder.classNames);
        this.total = builder.total;
        this.countdown = builder.countdown;
    }

    @Override
    public void run() {
        try (Predictor<Image, Classifications> predictor = model.newPredictor()) {

            List<Image> inputs = new ArrayList<>();
            List<PathObject> pathObjectBatch = new ArrayList<>();
            while (!pathObjects.isEmpty()) {
                inputs.clear();
                pathObjectBatch.clear();
                for (int i = 0; i < maxBatchSize; i++) {
                    PathObject pathObject = pathObjects.poll();
                    if (pathObject == null) {
                        break;
                    }

                    int count = total - countdown.decrementAndGet();
                    if (count % 100 == 0) {
                        logger.info("Processing {}/{}", count, total);
                    }

                    pathObjectBatch.add(pathObject);
                    ROI roi = pathObject.getROI();
                    int x = (int) Math.round(roi.getCentroidX() - width / 2.0);
                    int y = (int) Math.round(roi.getCentroidY() - height / 2.0);
                    BufferedImage img = server.readRegion(downsample, x, y, width, height);
                    if (resizeWidth > 0 && resizeHeight > 0)
                        img = BufferedImageTools.resize(img, resizeWidth, resizeHeight, true);
                    Image input = BufferedImageFactory.getInstance().fromImage(img);
//                            input = input.resize(resize, resize, false);
                    inputs.add(input);
                }
                if (inputs.isEmpty()) {
                    continue;
                }
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
            }
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
        }
    }


    static Builder builder() {
        return new Builder();
    }


    static class Builder {

        private ZooModel<Image, Classifications> model;
        private Queue<? extends PathObject> pathObjects;

        private int batchSize = 1;

        private ImageServer<BufferedImage> server;
        private double downsample = 1.0;
        private int width = 256;
        private int height = width;

        private List<String> classNames = new ArrayList<>();

        private int resizeWidth = -1;
        private int resizeHeight = -1;

        private int total;
        private AtomicInteger countdown;

        Builder tileQueue(Queue<? extends PathObject> queue) {
            this.pathObjects = queue;
            this.total = queue.size();
            this.countdown = new AtomicInteger(total);
            return this;
        }

        Builder server(ImageServer<BufferedImage> server) {
            this.server = server;
            return this;
        }

        Builder model(ZooModel<Image, Classifications> model) {
            this.model = model;
            return this;
        }

        Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        Builder downsample(double downsample) {
            this.downsample = downsample;
            return this;
        }

        Builder classNames(List<String> classNames) {
            this.classNames = new ArrayList<>(classNames);
            return this;
        }

        Builder tileSize(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        Builder resizeTile(int width, int height) {
            this.resizeWidth = width;
            this.resizeHeight = height;
            return this;
        }

        WSInferWorker build() {
            return new WSInferWorker(this);
        }

    }

}
