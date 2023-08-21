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

package qupath.ext.wsinfer;

import ai.djl.modality.cv.BufferedImageFactory;
import ai.djl.modality.cv.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.dnn.DnnTools;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Helper class to load image tiles in parallel, optionally with batching and resizing.
 */
class TileLoader {

    private static final Logger logger = LoggerFactory.getLogger(TileLoader.class);

    private final Queue<? extends PathObject> pathObjects;
    private final int maxBatchSize;

    private final ImageServer<BufferedImage> server;
    private final double downsample;
    private final int width;
    private final int height;

    private final int resizeWidth;
    private final int resizeHeight;

    private final BlockingQueue<TileBatch> batchQueue;

    private final int numWorkers;
    private final ExecutorService pool;

    private TileLoader(Builder builder) {
        this.pathObjects = new LinkedBlockingQueue<>(builder.pathObjects);
        this.maxBatchSize = builder.batchSize;
        this.server = builder.server;
        this.downsample = builder.downsample;
        this.width = builder.width;
        this.height = builder.height;
        this.resizeWidth = builder.resizeWidth;
        this.resizeHeight = builder.resizeHeight;

        this.numWorkers = builder.numWorkers;
        this.batchQueue = new ArrayBlockingQueue<>(builder.numWorkers * builder.numPrefetch);
        this.pool = Executors.newFixedThreadPool(builder.numWorkers,
                ThreadTools.createThreadFactory("wsinfer-tiles", true));

        for (int i = 0; i < builder.numWorkers; i++) {
            this.pool.execute(new TileWorker());
        }
        this.pool.shutdown();
    }

    /**
     * Get the queue containing batches.
     * This queue will be empty when all batches have been processed.
     * <p>
     * Each worker will also insert an empty batch into the queue when it finishes,
     * to indicate that the worker is finished.
     * <p>
     * The caller should therefore check that the number of empty batches taken from the
     * queue matches the number of worker to make sure that no batch is missed.
     * @return
     */
    public BlockingQueue<TileBatch> getBatchQueue() {
        return batchQueue;
    }

    /**
     * Get the original number of workers requested.
     * Not all workers may be active (i.e. some may have finished).
     * @return
     */
    public int getNumWorkers() {
        return numWorkers;
    }

    private TileBatch nextBatch() {
            List<Image> inputs = new ArrayList<>();
            List<PathObject> pathObjectBatch = new ArrayList<>();
            while (!pathObjects.isEmpty() && inputs.size() < maxBatchSize) {
                PathObject pathObject = pathObjects.poll();
                if (pathObject == null) {
                    break;
                }

                ROI roi = pathObject.getROI();
                int x = (int) Math.round(roi.getCentroidX() - width / 2.0);
                int y = (int) Math.round(roi.getCentroidY() - height / 2.0);
                try {
                    BufferedImage img;
                    if (x < 0 || y < 0 || x + width >= server.getWidth() || y + height >= server.getHeight()) {
                        // Handle out-of-bounds coordinates
                        // This reuses code from DnnTools.readPatch, but is not ideal since it uses a trip through OpenCV
                        var mat = DnnTools.readPatch(server, roi, downsample, width, height);
                        img = OpenCVTools.matToBufferedImage(mat);
                        mat.close();
                        logger.warn("Detected out-of-bounds tile request - results may be influenced by padding ({}, {}, {}, {})", x, y, width, height);
                    } else {
                        // Handle normal case of within-bounds coordinates
                        img = server.readRegion(downsample, x, y, width, height);
                        if (resizeWidth > 0 && resizeHeight > 0)
                            img = BufferedImageTools.resize(img, resizeWidth, resizeHeight, true);
                    }
                    Image input = BufferedImageFactory.getInstance().fromImage(img);
                    pathObjectBatch.add(pathObject);
                    inputs.add(input);
                } catch (IOException e) {
                    logger.error("Failed to read tile: {}", e.getMessage(), e);
                }
            }
            if (inputs.isEmpty())
                return new TileBatch();
            else
                return new TileBatch(inputs, pathObjectBatch);
    }

    class TileWorker implements Runnable {

        private final Logger logger = LoggerFactory.getLogger(TileWorker.class);

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    TileBatch batch = nextBatch();
                    batchQueue.put(batch);
                    if (batch.isEmpty())
                        return;
                }
            } catch (InterruptedException e) {
                logger.debug("Tile worker interrupted: {}", e.getMessage(), e);
            }
        }
    }

    public static class TileBatch {

        private List<Image> inputs;
        private List<PathObject> tiles;

        private TileBatch() {
            this.inputs = Collections.emptyList();
            this.tiles = Collections.emptyList();
        }

        private TileBatch(List<Image> inputs, List<PathObject> tiles) {
            this.inputs = Collections.unmodifiableList(inputs);
            this.tiles = Collections.unmodifiableList(tiles);
        }

        public List<Image> getInputs() {
            return inputs;
        }

        public List<PathObject> getTiles() {
            return tiles;
        }

        public boolean isEmpty() {
            return inputs.isEmpty();
        }

    }


    static Builder builder() {
        return new Builder();
    }


    static class Builder {

        private Collection<? extends PathObject> pathObjects;

        private int batchSize = 1;

        private ImageServer<BufferedImage> server;
        private double downsample = 1.0;
        private int width = 256;
        private int height = width;

        private int resizeWidth = -1;
        private int resizeHeight = -1;

        private int numWorkers = 4;
        private int numPrefetch = 2;

        /**
         * Parent tiles; their ROI centroids will be used to select the regions of inference
         * @param tiles
         * @return
         */
        Builder tiles(Collection<? extends PathObject> tiles) {
            this.pathObjects = new ArrayList<>(tiles);
            return this;
        }

        /**
         * ImageServer providing pixels for inference
         * @param server
         * @return
         */
        Builder server(ImageServer<BufferedImage> server) {
            this.server = server;
            return this;
        }

        /**
         * Preferred downsample factor for the image server when requesting tiles
         * @param downsample
         * @return
         */
        Builder downsample(double downsample) {
            this.downsample = downsample;
            return this;
        }

        /**
         * Preferred tile size for the image server requests, defined in terms of the full-resolution image
         * @param width
         * @param height
         * @return
         */
        Builder tileSize(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Optional width and height for the tiles to be resized to before inference.
         * Unlike #tileSize, these are defined in terms of the resized image.
         * @param width
         * @param height
         * @return
         */
        Builder resizeTile(int width, int height) {
            this.resizeWidth = width;
            this.resizeHeight = height;
            return this;
        }

        /**
         * Number of tiles to include in each batch.
         * @param batchSize
         * @return
         */
        Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Number of (possibly parallel) workers to use when loading tiles.
         * This must be at least 1.
         * @param numWorkers
         * @return
         */
        Builder numWorkers(int numWorkers) {
            if (numWorkers < 1)
                throw new IllegalArgumentException("Number of workers must be >= 1");
            this.numWorkers = numWorkers;
            return this;
        }

        /**
         * Number of tile batches for each worker to prefetch.
         * This must be at least 1.
         * @param numPrefetch
         * @return
         */
        Builder numPrefetch(int numPrefetch) {
            if (numPrefetch < 1)
                throw new IllegalArgumentException("Number of tiles to prefetch must be >= 1");
            this.numPrefetch = numPrefetch;
            return this;
        }

        /**
         * Build the TileLoader.
         * @return
         */
        TileLoader build() {
            return new TileLoader(this);
        }

    }

}
