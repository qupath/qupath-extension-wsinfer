package qupath.ext.wsinfer.models;

import java.io.File;

public class WSInferModelLocal extends WSInferModel {

    private final File modelDirectory;

    public WSInferModelLocal(File modelDirectory) {
        this.modelDirectory = modelDirectory;
        this.hfRepoId = modelDirectory.getName();
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
