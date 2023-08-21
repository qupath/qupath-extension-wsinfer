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
