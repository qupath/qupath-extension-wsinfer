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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.io.GsonTools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

// Equivalent to config.json files from hugging face
public class WSInferModel {

    private final Logger logger = LoggerFactory.getLogger(WSInferModel.class);

    private String description;
    private WSInferModelConfiguration configuration;

    @SerializedName("hf_repo_id")
    private String hfRepoId;

    @SerializedName("hf_revision")
    private String hfRevision;

    // transient means it's not serialized/deserialized
    private final transient BooleanProperty isModelAvailableProperty = new SimpleBooleanProperty(false);

    public String getName() {
        return hfRepoId;
    }

    /**
     * Get the configuration. Note that this may be null.
     * @return the model configuration, or null.
     */
    public WSInferModelConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = tryToLoadConfiguration();
        }
        return configuration;
    }

    /**
     * Remove the cached model files.
     */
    public synchronized void removeCache() {
        getTSFile().delete();
        getCFFile().delete();
    }

    /**
     * Get the torchscript file. Note that it is not guaranteed that the model has been downloaded.
     * @return path to torchscript pt file in cache dir
     */
    public File getTSFile() {
        return getFile("torchscript_model.pt");
    }

    /**
     * Get the configuration file. Note that it is not guaranteed that the model has been downloaded.
     * @return path to model config file in cache dir
     */
    public File getCFFile() {
        return getFile("config.json");
    }

    /**
     * Check if the model files exist and are valid.
     * @return true if the files exist and the SHA matches, and the config is valid.
     */
    public boolean isValid() {
        return getTSFile().exists() && checkSHAMatches() && getConfiguration() != null;
    }

    /**
     * True if the model files exist and have been SHA matched.
     * @return a read/write {@link BooleanProperty}.
     */
    public BooleanProperty isModelAvailableProperty() {
        return this.isModelAvailableProperty;
    }

    private File getFile(String f) {
        return new File(String.format("%s/%s", getModelDirectory(), f));
    }

    private File getModelDirectory() {
        return new File(
                String.format(
                        "%s" + File.separator + "%s" + File.separator + "/%s",
                        WSInferPrefs.modelDirectoryProperty().get(), hfRepoId, hfRevision));
    }

    private WSInferModelConfiguration tryToLoadConfiguration() {
        var cfFile = getCFFile();
        if (cfFile.exists()) {
            try (var reader = Files.newBufferedReader(cfFile.toPath(), StandardCharsets.UTF_8)) {
                return GsonTools.getInstance().fromJson(reader, WSInferModelConfiguration.class);
            } catch (IOException e) {
                logger.error("Cannot read configuration {}", cfFile, e);
            }
        }
        return null;
    }

    private static String SHA256(File file) throws IOException, NoSuchAlgorithmException {
        byte[] data = Files.readAllBytes(file.toPath());
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        return new BigInteger(1, hash).toString(16);
    }

    private boolean checkSHAMatches() {
        try {
            String shaDown = SHA256(getTSFile());
            String shaUp = downloadSHA();
            if (!shaDown.equals(shaUp)) {
                return false;
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Unable to generate SHA for {}", getTSFile(), e);
            return false;
        }
        return true;
    }

    private String downloadSHA() throws IOException {
        URL url = new URL(String.format("https://huggingface.co/%s/raw/%s/torchscript_model.pt", hfRepoId, hfRevision));
        // this is the format
//        Result: version https://git-lfs.github.com/spec/v1
//        oid sha256:fffeeecb4282b61b2b699c6dfcd8f76c30c8ca1af9800fa78f5d81fc0b78a4e2
//        size 94494278
        try (InputStream stream = url.openStream()) {
            String out = new Scanner(stream, StandardCharsets.UTF_8)
                    .useDelimiter("\\A")
                    .next();
            return out.split("\n")[1]
                    .replace("oid sha256:", "");
        }
    }

    /**
     * Request that the model is downloaded.
     */
    public synchronized void downloadModel() {
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
        File modelDirectory = getModelDirectory();
        if (!modelDirectory.exists()) {
            try {
                Files.createDirectories(modelDirectory.toPath());
            } catch (IOException e) {
                logger.error("Cannot create directory for model files {}", modelDirectory, e);
            }
        }
        File tsFile = getTSFile();
        WSInferUtils.downloadURLToFile(tsURL, tsFile);
        File cfFile = getCFFile();
        WSInferUtils.downloadURLToFile(cfURL, cfFile);
        isModelAvailableProperty.set(isValid());
    }
}
