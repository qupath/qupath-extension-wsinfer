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
    private ModelConfiguration configuration;

    @SerializedName("hf_repo_id")
    private String hfRepoId;

    @SerializedName("hf_revision")
    private String hfRevision;

    public String getName() {
        return hfRepoId;
    }

    public ModelConfiguration getConfiguration() {
        return configuration;
    }

    public void removeCache() {
        getTSFile().delete();
        getCFFile().delete();
    }

    public File getTSFile() {
        return getFile("torchscript_model.pt");
    }

    public File getCFFile() {
        return getFile("config.json");
    }

    public File getFile(String f) {
        String dir = WSInferPrefs.modelDirectoryProperty().get();
        String modPath = String.format("%s" + File.separator + "%s" + File.separator + "/%s", dir, hfRepoId, hfRevision);
        return new File(String.format("%s/%s", modPath, f));
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
}
