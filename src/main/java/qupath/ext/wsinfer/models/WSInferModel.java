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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.io.GsonTools;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// Equivalent to config.json files from hugging face
public class WSInferModel {

    private final Logger logger = LoggerFactory.getLogger(WSInferModel.class);

    private String description;
    private WSInferModelConfiguration configuration;

    @SerializedName("hf_repo_id")
    private String hfRepoId;

    @SerializedName("hf_revision")
    private String hfRevision;

    public String getName() {
        return hfRepoId;
    }

    /**
     * Get the configuration. Note that this may be null.
     * @return
     */
    public WSInferModelConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = tryToLoadConfiguration();
        }
        return configuration;
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

    /**
     * Remove the cached model files.
     */
    public synchronized void removeCache() {
        getTSFile().delete();
        getCFFile().delete();
    }

    /**
     * Get the torchscript file. Note that it is not guaranteed that the model has been downloaded.
     * @return
     */
    public File getTSFile() {
        return getFile("torchscript_model.pt");
    }

    /**
     * Get the configuration file. Note that it is not guaranteed that the model has been downloaded.
     * @return
     */
    public File getCFFile() {
        return getFile("config.json");
    }

    private File getFile(String f) {
        String dir = WSInferPrefs.modelDirectoryProperty().get();
        String modPath = String.format("%s" + File.separator + "%s" + File.separator + "/%s", dir, hfRepoId, hfRevision);
        return new File(String.format("%s/%s", modPath, f));
    }

    /**
     * Query if the model has already been downloaded.
     * @return
     */
    public boolean isModelAvailable() {
        return getTSFile().exists() && getConfiguration() != null;
    }

    /**
     * Request that the model is downloaded.
     */
    public synchronized void fetchModel() {
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
    }
}
