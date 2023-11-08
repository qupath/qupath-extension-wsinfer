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
 import java.math.BigInteger;
 import java.net.URL;
 import java.nio.charset.StandardCharsets;
 import java.nio.file.Files;
 import java.nio.file.Paths;
 import java.security.MessageDigest;
 import java.security.NoSuchAlgorithmException;

// Equivalent to config.json files from hugging face
public class WSInferModel {

    private static final Logger logger = LoggerFactory.getLogger(WSInferModel.class);

    private String description;
    private WSInferModelConfiguration configuration;

    @SerializedName("hf_repo_id")
    String hfRepoId;

    @SerializedName("hf_revision")
    String hfRevision;

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
     * Get the configuration file. Note that it is not guaranteed that the model has been downloaded.
     * @return path to model config file in cache dir
     */
    public File getREADMEFile() {
        return getFile("README.md");
    }

    /**
     * Check if the model files exist and are valid.
     * @return true if the files exist and the SHA matches, and the config is valid.
     */
    public boolean isValid() {
        return getTSFile().exists() && checkModifiedTimes() && getConfiguration() != null;
    }

    /**
     * Check if the LFS pointer that contains the SHA has later modified time
     * than the model file. This should always be true since we download the
     * model first.
     * @return true if the modified times are as expected.
     */
    private boolean checkModifiedTimes() {
        try {
            return Files.getLastModifiedTime(getTSFile().toPath())
                    .compareTo(Files.getLastModifiedTime(getPointerFile().toPath())) < 0;
        } catch (IOException e) {
            logger.error("Cannot get last modified time");
            return false;
        }
    }

    private File getPointerFile() {
        return getFile("lfs-pointer.txt");
    }

    private File getFile(String f) {
        return Paths.get(getModelDirectory().toString(), f).toFile();
    }

    File getModelDirectory() {
        return Paths.get(WSInferPrefs.modelDirectoryProperty().get(), hfRepoId, hfRevision).toFile();
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

    private static String checkSumSHA256(File file) throws IOException, NoSuchAlgorithmException {
        byte[] data = Files.readAllBytes(file.toPath());
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        return new BigInteger(1, hash).toString(16);
    }

    /**
     * Check that the SHA-256 checksum in the LFS pointer file matches one
     * we calculate ourselves.
     * @return true if the torchscript model file is identical to the remote one.
     */
    private boolean checkSHAMatches() {
        try {
            String shaDown = checkSumSHA256(getTSFile());
            // this is the format
            //        Result: version https://git-lfs.github.com/spec/v1
            //        oid sha256:fffeeecb4282b61b2b699c6dfcd8f76c30c8ca1af9800fa78f5d81fc0b78a4e2
            //        size 94494278
            String content = Files.readString(getPointerFile().toPath(), StandardCharsets.UTF_8);
            String shaUp = content.split("\n")[1]
                    .replace("oid sha256:", "");
            if (!shaDown.equals(shaUp)) {
                return false;
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Unable to generate SHA for {}", getTSFile(), e);
            return false;
        }
        return true;
    }



    /**
     * Request that the model is downloaded.
     */
    public synchronized void downloadModel() throws IOException {
        File modelDirectory = getModelDirectory();
        if (!modelDirectory.exists()) {
            Files.createDirectories(modelDirectory.toPath());
        }
        downloadFileToCacheDir("torchscript_model.pt");
        downloadFileToCacheDir("config.json");
        downloadFileToCacheDir("README.md");

        // this downloads the LFS pointer, not the actual .pt file
        // the LFS pointer contains a SHA256 checksum
        URL url = new URL(String.format("https://huggingface.co/%s/raw/%s/torchscript_model.pt", hfRepoId, hfRevision));
        WSInferUtils.downloadURLToFile(url, getPointerFile());
        if (!isValid() || !checkSHAMatches()) {
            throw new IOException("Error downloading model files");
        }

    }

    private void downloadFileToCacheDir(String file) throws IOException {
        URL url = new URL(String.format("https://huggingface.co/%s/resolve/%s/%s", hfRepoId, hfRevision, file));
        WSInferUtils.downloadURLToFile(url, getFile(file));
    }
}
