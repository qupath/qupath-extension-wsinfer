package qupath.ext.wsinfer.models;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.gui.dialogs.Dialogs;

import java.util.ArrayList;

public class WSIModelRunner {
    private final WSIModel wsiModel;
    private final BooleanProperty downloadRequestedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty modelIsReadyProperty = new SimpleBooleanProperty(false);
    
    public WSIModelRunner(WSIModel wsiModel) {
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

    public WSIModel getModel() {
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
