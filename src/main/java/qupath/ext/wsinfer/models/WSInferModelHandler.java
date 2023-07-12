package qupath.ext.wsinfer.models;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.gui.dialogs.Dialogs;

public class WSInferModelHandler {
    private final WSInferModel wsiModel;
    private final BooleanProperty downloadRequestedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty modelIsReadyProperty = new SimpleBooleanProperty(false);
    
    public WSInferModelHandler(WSInferModel wsiModel) {
        this.wsiModel = wsiModel;
        this.downloadRequestedProperty.addListener((v, o, n) -> {
            if (n) {
                Dialogs.showPlainNotification("WSInfer", String.format("Fetching model: %s", wsiModel.getName()));
                wsiModel.fetchModel();
                Dialogs.showPlainNotification("WSInfer", String.format("Model available: %s", wsiModel.getName()));
                downloadRequestedProperty.set(false);
                modelIsReadyProperty.set(true);
            }
        });
    }

    public WSInferModel getModel() {
        return wsiModel;
    }

    public void queueDownload(boolean force) {
        if (force) {
            wsiModel.removeCache();
        }
        this.downloadRequestedProperty.set(true);
    }

    public BooleanProperty modelIsReadyProperty() {
        return this.modelIsReadyProperty;
    }
}
