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

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;


import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Command to open the QuPath-WSInfer user interface.
 */
public class WSInferCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WSInferCommand.class);

    private final QuPathGUI qupath;

    private final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");

    private Stage stage;
    private WSInferController controller;

    public WSInferCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (stage == null) {
            try {
                stage = createStage();
                stage.show();
                FXUtils.retainWindowPosition(stage);
            } catch (IOException e) {
                Dialogs.showErrorMessage(resources.getString("title"),
                        resources.getString("error.window"));
                logger.error(e.getMessage(), e);
                return;
            }
        } else {
            controller.refreshAvailableModels();
            stage.show();
        }
    }

    private Stage createStage() throws IOException {

        URL url = getClass().getResource("wsinfer_control.fxml");
        if (url == null) {
            throw new IOException("Cannot find URL for WSInfer FXML");
        }

        // We need to use the ExtensionClassLoader to load the FXML, since it's in a different module
        var loader = new FXMLLoader(url, resources);
        loader.setClassLoader(this.getClass().getClassLoader());
        VBox root = loader.load();
        controller = loader.getController();

        // There's probably a better approach... but wrapping in a border pane
        // helped me get the resizing to behave
        BorderPane pane = new BorderPane(root);
        Scene scene = new Scene(pane);

        Stage stage = new Stage();
        stage.initOwner(qupath.getStage());
        stage.setTitle(resources.getString("title"));
        stage.setScene(scene);
        stage.setResizable(false);

        root.heightProperty().addListener((v, o, n) -> handleStageHeightChange());

        return stage;
    }

    private void handleStageHeightChange() {
        stage.sizeToScene();
        // This fixes a bug where the stage would migrate to the corner of a screen if it is
        // resized, hidden, then shown again
        if (stage.isShowing() && Double.isFinite(stage.getX()) && Double.isFinite(stage.getY()))
            FXUtils.retainWindowPosition(stage);
    }


}
