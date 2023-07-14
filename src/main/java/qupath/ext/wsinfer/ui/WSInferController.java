package qupath.ext.wsinfer.ui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.WSInfer;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.ext.wsinfer.models.WSInferModelCollection;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the WSInfer user interface, which is defined in wsinfer_control.fxml
 * and loaded by WSInferCommand.
 */
public class WSInferController {

    private static final Logger logger = LoggerFactory.getLogger(WSInferController.class);

    @FXML
    private ChoiceBox<String> modelChoiceBox;
    @FXML
    private Button runButton;
    @FXML
    private Button forceRefreshButton;

    private WSInferModelHandler currentRunner;
    private Stage measurementMapsStage;

    private ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("wsinfer", true));

    private ObjectProperty<WSInferTask> pendingTask = new SimpleObjectProperty<>();

    @FXML
    private void initialize() {
        logger.info("Initializing...");
        WSInferModelCollection models = WSInferUtils.getModelCollection();
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
//            if (oldRunner != null) {
//                oldRunner.modelIsReadyProperty().removeListener(this::changed);
//            }

            new Thread(() -> {
                currentRunner.queueDownload(false);
                if (currentRunner.modelIsReadyProperty().get()) {
//                    changed(null, false, true);
                } else {
//                    currentRunner.modelIsReadyProperty().addListener(this::changed);
                }
            }).start();
        });

        // Disable the run button while a task is pending, or we have no model selected
        runButton.disableProperty().bind(
                pendingTask.isNotNull().or(
                        modelChoiceBox.getSelectionModel().selectedItemProperty().isNull()));

        // Submit any new pending tasks to the thread pool
        pendingTask.addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                pool.execute(newValue);
            }
        });
    }

    /**
     * Try to run inference on the current image using the current model and parameters.
     */
    public void runInference() {
        var imageData = QuPathGUI.getInstance().getImageData();
        if (imageData == null) {
            Dialogs.showErrorMessage("WSInfer plugin", "Cannot run WSInfer plugin without ImageData.");
            return;
        }
        var model = currentRunner.getModel();
        submitInferenceTask(imageData, model.getName());
    }

    private void submitInferenceTask(ImageData<BufferedImage> imageData, String modelName) {
        var task = new WSInferTask(imageData, currentRunner.getModel());
        pendingTask.set(task);
        // Reset the pending task when it completes (either successfully or not)
        task.stateProperty().addListener((observable, oldValue, newValue) -> {
            if (Set.of(Worker.State.CANCELLED, Worker.State.SUCCEEDED, Worker.State.FAILED).contains(newValue)) {
                if (pendingTask.get() == task)
                    pendingTask.set(null);
            }
        });
    }

    private static void addToHistoryWorkflow(ImageData<?> imageData, String modelName) {
        imageData.getHistoryWorkflow()
                .addStep(
                        new DefaultScriptableWorkflowStep(
                                "Run WSInfer model",
                                WSInfer.class.getName() + ".runInference(\"" + modelName + "\")"
                        ));
    }

    public void forceRefresh() {
        new Thread(() -> {
            currentRunner.modelIsReadyProperty().set(false);
            currentRunner.queueDownload(true);
//            currentRunner.modelIsReadyProperty().addListener(this::changed);
        }).start();
    }

    @FXML
    private void openMeasurementMaps() {
        if (measurementMapsStage == null) {
            measurementMapsStage = Commands.createMeasurementMapDialog(QuPathGUI.getInstance());
        }
        measurementMapsStage.show();
    }


    /**
     * Wrapper for an inference task, which can be submitted to the thread pool.
     */
    private static class WSInferTask extends Task<Void> {

        private final ImageData<BufferedImage> imageData;
        private final WSInferModel model;

        private WSInferTask(ImageData<BufferedImage> imageData, WSInferModel model) {
            this.imageData = imageData;
            this.model = model;
        }

        @Override
        protected Void call() throws Exception {
            try {
                Platform.runLater(() -> Dialogs.showInfoNotification(getTitle(), "Requesting inference for " + model.getName()));
                WSInfer.runInference(imageData, model);
                addToHistoryWorkflow(imageData, model.getName());
            } catch (InterruptedException e) {
                Platform.runLater(() -> Dialogs.showErrorNotification(getTitle(), e.getLocalizedMessage()));
            } catch (Exception e) {
                Platform.runLater(() -> Dialogs.showErrorMessage(getTitle(), e.getLocalizedMessage()));
            }
            return null;
        }

    }

}
