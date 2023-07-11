package qupath.ext.wsinfer.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.ModelCollection;
import qupath.ext.wsinfer.models.WSIModel;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.lib.gui.dialogs.Dialogs;

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

    private WSIModel model;

    @FXML
    private void initialize() {
        logger.info("Initializing...");
        ModelCollection models = WSInferUtils.parseModels();
        for (String key: models.getModels().keySet()) {
            modelChoiceBox.getItems().add(key);
        }
        modelChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            model = models.getModels().get(newValue);
            runButton.setDisable(true);
            new Thread(() -> {
                Dialogs.showPlainNotification("WSInfer", String.format("Fetching model: %s", model.getName()));
                model.fetchModel();
                Dialogs.showPlainNotification("WSInfer", String.format("Model available: %s", model.getName()));
                runButton.setDisable(false);
            }).start();
        });
    }

    public void run(ActionEvent actionEvent) {
        this.model.runModel();
    }
}
