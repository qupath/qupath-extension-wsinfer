package qupath.ext.wsinfer.ui;

import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.WSInferModelCollection;
import qupath.ext.wsinfer.models.WSInferModelHandler;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.scripting.QP;

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

    private WSInferModelHandler currentRunner;
    private ImageData imageData;
    private Stage measurementMapsStage;

    @FXML
    private void initialize() {
        logger.info("Initializing...");
        WSInferModelCollection models = WSInferUtils.parseModels();
        imageData = QP.getCurrentImageData();
        Map<String, WSInferModelHandler> runners = new HashMap<>();
        for (String key: models.getModels().keySet()) {
            WSInferModelHandler runner = new WSInferModelHandler(models.getModels().get(key));
            runners.put(key, runner);
            modelChoiceBox.getItems().add(key);
        }

        forceRefreshButton.setDisable(true);

        modelChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            forceRefreshButton.setDisable(false);
            currentRunner = runners.get(newValue);
            WSInferModelHandler oldRunner = runners.get(oldValue);
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
        imageData = QP.getCurrentImageData();
        if (imageData == null) {
            Dialogs.showErrorMessage("WSInfer plugin", "Cannot run WSInfer plugin without ImageData.");
            return;
        }
        WSInferCommand.runInference(currentRunner.getModel());
        String mName = modelChoiceBox.getValue();
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Parse WSInfer model JSON",
                                "ModelCollection models = WSInferUtils.parseModels();"
                        ));
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Define model name",
                                "def modelName = " + mName + ";"
                        ));
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Fetch WSInfer model",
                                "models.getModels().get(modelName).fetchModel();"
                ));
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Run WSInfer model",
                                "WSInferCommand.runInference(models.getModels().get(modelName));"
                ));
        openMeasurementMaps();
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

    @FXML
    private void openMeasurementMaps() {
        if (measurementMapsStage == null) {
            measurementMapsStage = Commands.createMeasurementMapDialog(QuPathGUI.getInstance());
        }
        measurementMapsStage.show();
    }
}
