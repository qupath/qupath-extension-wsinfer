package qupath.ext.wsinfer.models;

import qupath.ext.wsinfer.ui.WSInferPrefs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WSInferModelLocal extends WSInferModel {

    private final File modelDirectory;

    public WSInferModelLocal(File modelDirectory) {
        this.modelDirectory = modelDirectory;
        this.hfRepoId = modelDirectory.getName();
        // todo: load any files, populate fields from them.
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
