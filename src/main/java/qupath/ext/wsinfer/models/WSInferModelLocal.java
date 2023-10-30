package qupath.ext.wsinfer.models;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public class WSInferModelLocal extends WSInferModel {

    private final File modelDirectory;
    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");

    /**
     * Try to create a WSInfer model from a user directory.
     * @param modelDirectory A user directory containing a
     *                       torchscript.pt file and a config.json file
     * @return A {@link WSInferModel} if the directory supplied is valid,
     * otherwise nothing.
     */
    public static WSInferModelLocal createInstance(File modelDirectory) throws IOException {
        return new WSInferModelLocal(modelDirectory);
    }

    private WSInferModelLocal(File modelDirectory) throws IOException {
        this.modelDirectory = modelDirectory;
        this.hfRepoId = modelDirectory.getName();
        List<File> files = Arrays.asList(Objects.requireNonNull(modelDirectory.listFiles()));
        if (!files.contains(getCFFile())) {
            throw new IOException(resources.getString("error.localModel") + ": " + getCFFile().toString());
        }
        if (!files.contains(getTSFile())) {
            throw new IOException(resources.getString("error.localModel") + ": " + getTSFile().toString());
        }
    }

    @Override
    File getModelDirectory() {
        return this.modelDirectory;
    }

    @Override
    public boolean isValid() {
        return getTSFile().exists() && getConfiguration() != null;
    }

    @Override
    public synchronized void downloadModel() {}

    @Override
    public synchronized void removeCache() {}
}
