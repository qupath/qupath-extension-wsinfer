package qupath.ext.wsinfer.ui;

import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.ModelCollection;
import qupath.ext.wsinfer.models.WSIModelHandler;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

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

    private WSIModelHandler currentRunner;
    private ImageData imageData;


    @FXML
    private void initialize() {
        logger.info("Initializing...");
        ModelCollection models = WSInferUtils.parseModels();
        imageData = QuPathGUI.getInstance().getImageData();
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep("Parse WSInfer model JSON", "ModelCollection models = WSInferUtils.parseModels();"
                ));
        Map<String, WSIModelHandler> runners = new HashMap<>();
        for (String key: models.getModels().keySet()) {
            WSIModelHandler runner = new WSIModelHandler(models.getModels().get(key));
            runners.put(key, runner);
            modelChoiceBox.getItems().add(key);
        }
        modelChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            currentRunner = runners.get(newValue);
            WSIModelHandler oldRunner = runners.get(oldValue);
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
        String mName = currentRunner.getModel().getName();
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Fetch WSInfer model",
                                "models.get(" + mName + ").fetchModel()"
                ));
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Run WSInfer model",
                                "WSInferCommand.runInference(models.get(" + mName + ");"
                ));
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
