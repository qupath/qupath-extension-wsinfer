package qupath.ext.wsinfer.ui;

import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.ModelCollection;
import qupath.ext.wsinfer.models.WSIModel;
import qupath.ext.wsinfer.models.WSIModelRunner;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.lib.gui.dialogs.Dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the WSInfer user interface, which is defined in wsinfer_control.fxml
 * and loaded by WSInferCommand.
 */
public class WSInferController {

    private static final Logger logger = LoggerFactory.getLogger(WSInferController.class);

    private static final String TITLE = "WSInfer";

    @FXML
    private ChoiceBox<String> modelChoiceBox;
    @FXML
    private Button runButton;
    @FXML
    private Button forceRefreshButton;

    private WSIModelRunner currentRunner;


    @FXML
    private void initialize() {
        logger.info("Initializing...");
        ModelCollection models = WSInferUtils.parseModels();
        Map<String, WSIModelRunner> runners = new HashMap<>();
        for (String key: models.getModels().keySet()) {
            WSIModelRunner runner = new WSIModelRunner(models.getModels().get(key));
            runners.put(key, runner);
            modelChoiceBox.getItems().add(key);
        }
        modelChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            currentRunner = runners.get(newValue);
            WSIModelRunner oldRunner = runners.get(oldValue);
            if (oldRunner != null) {
                oldRunner.modelIsReadyProperty().removeListener(this::changed);
            }

            runButton.setDisable(true);
            new Thread(() -> {
                currentRunner.queueDownload(false);
                if (currentRunner.modelIsReadyProperty().get()) {
                    changed(null, false, true);
                } else {
                    currentRunner.modelIsReadyProperty().addListener(this::changed);
                }
            }).start();
        });
    }

    public void run() {
        WSInferCommand.runInference(currentRunner.getModel());
    }

    public void forceRefresh() {
        runButton.setDisable(true);
        new Thread(() -> {
            currentRunner.modelIsReadyProperty().set(false);
            currentRunner.queueDownload(true);
            currentRunner.modelIsReadyProperty().addListener(this::changed);
        }).start();
    }

    private void changed(ObservableValue<? extends Boolean> v, Boolean o, Boolean n) {
        runButton.setDisable(!n);
    }
}
