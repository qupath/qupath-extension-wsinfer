package qupath.ext.wsinfer.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.lib.gui.dialogs.Dialogs;

import java.util.ResourceBundle;

public class WSInferModelHandler {

    private final WSInferModel wsiModel;
    private final BooleanProperty downloadRequestedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty modelIsReadyProperty = new SimpleBooleanProperty(false);

    private final static ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");
    
    public WSInferModelHandler(WSInferModel wsiModel) {
        this.wsiModel = wsiModel;
        this.downloadRequestedProperty.addListener((v, o, n) -> {
            if (n) {
                Dialogs.showPlainNotification(resources.getString("title"), String.format(resources.getString("ui.popup.fetching"), wsiModel.getName()));
                wsiModel.fetchModel();
                Dialogs.showPlainNotification(resources.getString("title"), String.format(resources.getString("ui.popup.available"), wsiModel.getName()));
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
