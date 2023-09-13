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

import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.io.GsonTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class to help with working with WSInfer models.
 */
public class WSInferUtils {

    private static final Logger logger = LoggerFactory.getLogger(WSInferUtils.class);

    private static WSInferModelCollection cachedModelCollection;

    static void downloadURLToFile(URL url, File file) throws IOException {
        try (InputStream stream = url.openStream()) {
            try (ReadableByteChannel readableByteChannel = Channels.newChannel(stream)) {
                try (FileChannel channel = FileChannel.open(file.toPath())) {
                    channel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
            }
        }
    }

    /**
     * Get the model collection, downloading it if necessary.
     * @return
     */
    public static WSInferModelCollection getModelCollection() {
        if (cachedModelCollection == null) {
            synchronized (WSInferUtils.class) {
                if (cachedModelCollection == null)
                    cachedModelCollection = downloadModelCollection();
            }
        }
        return cachedModelCollection;
    }

    /**
     * Download the model collection from the hugging face repo.
     * This replaces any previously cached version.
     * Usually, {@link #getModelCollection()} should be used instead, so that the model collection is only downloaded
     * if it is not already cached.
     * @return
     */
    public static WSInferModelCollection downloadModelCollection() {
        cachedModelCollection = downloadModelCollectionImpl();
        return cachedModelCollection;
    }

    /**
     * Check if a directory exists and create it if it does not.
     * @param path the path of the directory
     * @return true if the directory exists when the method returns
     */
    public static boolean checkPathExists(Path path) {
        if (!path.toFile().exists()) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                logger.error("Cannot create directory {}", path, e);
                return false;
            }
        }
        return true;
    }

    private static Path getCachedCollectionFile() {
        return Paths.get(WSInferPrefs.modelDirectoryProperty().get(), "wsinfer-zoo-registry.json");
    }

    private static WSInferModelCollection downloadModelCollectionImpl() {
        String json;
        URL url = null;
        try {
            url = new URL("https://huggingface.co/datasets/kaczmarj/wsinfer-model-zoo-json/raw/main/wsinfer-zoo-registry.json");
        } catch (MalformedURLException e) {
            logger.error("Malformed URL", e);
        }
        Path cachedFile = getCachedCollectionFile();
        try {
            checkPathExists(Path.of(WSInferPrefs.modelDirectoryProperty().get()));
            downloadURLToFile(url, cachedFile.toFile());
            logger.info("Downloaded zoo file {}", cachedFile);
        } catch (IOException e) {
            logger.error("Unable to download zoo JSON file {}", cachedFile, e);
        }
        try {
            json = Files.readString(cachedFile);
            logger.info("Read cached zoo file {}", cachedFile);
        } catch (IOException e) {
            logger.error("Unable to read cached zoo JSON file", e);
            return null;
        }
        return GsonTools.getInstance().fromJson(json, WSInferModelCollection.class);
    }

}
