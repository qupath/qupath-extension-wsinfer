package qupath.ext.wsinfer.ui;

import ai.djl.engine.Engine;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ProgressListener;
import qupath.ext.wsinfer.WSInfer;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.ext.wsinfer.models.WSInferModelCollection;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    public QuPathGUI qupath;
    private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
    @FXML
    private ChoiceBox<String> modelChoiceBox;
    @FXML
    private Button runButton;
    @FXML
    private Button forceRefreshButton;
    @FXML
    private ChoiceBox<String> deviceChoices;
    @FXML
    private ToggleButton toggleSelectAllAnnotations;
    @FXML
    private ToggleButton toggleSelectAllDetections;
    @FXML
    private ToggleButton toggleDetectionFill;
    @FXML
    private ToggleButton toggleDetections;
    @FXML
    private ToggleButton toggleAnnotations;
    @FXML
    private Slider sliderOpacity;
    @FXML
    private Spinner<Integer> spinnerNumWorkers;
    @FXML
    private TextField tfModelDirectory;

    private WSInferModelHandler currentRunner;
    private Stage measurementMapsStage;

    private final ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("wsinfer", true));

    private final ObjectProperty<WSInferTask> pendingTask = new SimpleObjectProperty<>();


    @FXML
    private void initialize() {
        logger.info("Initializing...");

        this.qupath = QuPathGUI.getInstance();
        this.imageDataProperty.bind(qupath.imageDataProperty());

        configureModelChoices();

        configureSelectionButtons();
        configureDisplayToggleButtons();
        configureOpacitySlider();

        configureAvailableDevices();
        configureModelDirectory();
        configureNumWorkers();

        configureRunInferenceButton();

        configurePendingTaskProperty();
    }

    /**
     * Populate the available models & configure the UI elements to select and download models.
     */
    private void configureModelChoices() {
        WSInferModelCollection models = WSInferUtils.getModelCollection();
        Map<String, WSInferModelHandler> runners = new HashMap<>();
        for (String key: models.getModels().keySet()) {
            WSInferModelHandler runner = new WSInferModelHandler(models.getModels().get(key));
            runners.put(key, runner);
            modelChoiceBox.getItems().add(key);
        }
        modelChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            currentRunner = runners.get(newValue);
            new Thread(() -> currentRunner.queueDownload(false)).start();
        });
        forceRefreshButton.disableProperty().bind(modelChoiceBox.getSelectionModel().selectedItemProperty().isNull());
    }


    private void configureAvailableDevices() {
        var available = getAvailableDevices();
        deviceChoices.getItems().setAll(available);
        var selected = WSInferPrefs.deviceProperty().get();
        if (available.contains(selected))
            deviceChoices.getSelectionModel().select(selected);
        else
            deviceChoices.getSelectionModel().selectFirst();
        // Don't bind property for now, since this would cause trouble if the WSInferPrefs.deviceProperty() is
        // changed elsewhere
        deviceChoices.getSelectionModel().selectedItemProperty().addListener((value, oldValue, newValue) -> {
            WSInferPrefs.deviceProperty().set(newValue);
        });
    }


    private Collection<String> getAvailableDevices() {
        Set<String> availableDevices = new LinkedHashSet<>();
        boolean includesMPS = false; // Don't add MPS twice
        try {
            for (var device : Engine.getEngine("PyTorch").getDevices()) {
                String name = device.getDeviceType();
                availableDevices.add(name);
                if (name.toLowerCase().startsWith("mps"))
                    includesMPS = true;
            }
        } catch (Throwable e) {
            logger.warn("PyTorch not found");
            availableDevices.add("cpu");
        }
        // If we could use MPS, but don't have it already, add it
        if (!includesMPS && GeneralTools.isMac() && "aarch64".equals(System.getProperty("os.arch"))) {
            availableDevices.add("mps");
        }
        return availableDevices;
    }


    private void configureRunInferenceButton() {
        // Disable the run button while a task is pending, or we have no model selected
        runButton.disableProperty().bind(
                imageDataProperty.isNull()
                        .or(pendingTask.isNotNull())
                        .or(modelChoiceBox.getSelectionModel().selectedItemProperty().isNull())
        );
    }

    private void configurePendingTaskProperty() {
        // Submit any new pending tasks to the thread pool
        pendingTask.addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                pool.execute(newValue);
            }
        });
    }

    private void configureDisplayToggleButtons() {
        var actions = qupath.getDefaultActions();
        configureActionToggleButton(actions.FILL_DETECTIONS, toggleDetectionFill);
        configureActionToggleButton(actions.SHOW_DETECTIONS, toggleDetections);
        configureActionToggleButton(actions.SHOW_ANNOTATIONS, toggleAnnotations);
    }

    private void configureOpacitySlider() {
        var opacityProperty = qupath.getOverlayOptions().opacityProperty();
        sliderOpacity.valueProperty().bindBidirectional(opacityProperty);
    }

    private void configureSelectionButtons() {
        toggleSelectAllAnnotations.disableProperty().bind(imageDataProperty.isNull());
        overrideToggleSelected(toggleSelectAllAnnotations);
        toggleSelectAllDetections.disableProperty().bind(imageDataProperty.isNull());
        overrideToggleSelected(toggleSelectAllDetections);
    }

    // Hack to prevent the toggle buttons from staying selected
    // This allows us to use a segmented button with the appearance of regular, non-toggle buttons
    private void overrideToggleSelected(ToggleButton button) {
        button.selectedProperty().addListener((value, oldValue, newValue) -> button.setSelected(false));
    }

    /**
     * Configure a toggle button for showing/hiding or filling/unfilling objects.
     * @param action
     * @param button
     */
    private void configureActionToggleButton(Action action, ToggleButton button) {
        ActionUtils.configureButton(action, button);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void configureModelDirectory() {
        tfModelDirectory.textProperty().bindBidirectional(WSInferPrefs.modelDirectoryProperty());
    }

    private void configureNumWorkers() {
        spinnerNumWorkers.getValueFactory().valueProperty().bindBidirectional(WSInferPrefs.numWorkersProperty());
    }

    /**
     * Try to run inference on the current image using the current model and parameters.
     */
    public void runInference() {
        var imageData = this.imageDataProperty.get();
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
        }).start();
    }

    @FXML
    private void selectAllAnnotations() {
        Commands.selectObjectsByClass(imageDataProperty.get(), PathAnnotationObject.class);
    }

    @FXML
    private void selectAllTiles() {
        Commands.selectObjectsByClass(imageDataProperty.get(), PathTileObject.class);
    }

    @FXML
    private void openMeasurementMaps(ActionEvent event) {
        // Try to use existing action, to avoid creating a new stage
        // TODO: Replace this if QuPath v0.5.0 provides direct access to the action
        //       since that should be more robust
        var action = qupath.lookupActionByText("Show measurement maps");
        if (action != null) {
            action.handle(event);
            return;
        }
        // Fallback in case we couldn't get the action
        if (measurementMapsStage == null) {
            logger.warn("Creating a new measurement map stage");
            measurementMapsStage = Commands.createMeasurementMapDialog(QuPathGUI.getInstance());
        }
        measurementMapsStage.show();
    }

    @FXML
    private void openDetectionTable() {
        Commands.showDetectionMeasurementTable(qupath, imageDataProperty.get());
    }



    /**
     * Wrapper for an inference task, which can be submitted to the thread pool.
     */
    private static class WSInferTask extends Task<Void> {

        private final ImageData<BufferedImage> imageData;
        private final WSInferModel model;
        private final ProgressListener progressListener;

        private WSInferTask(ImageData<BufferedImage> imageData, WSInferModel model) {
            this.imageData = imageData;
            this.model = model;
            this.progressListener = new WSInferProgressDialog(QuPathGUI.getInstance().getStage(), e -> {
                if (Dialogs.showYesNoDialog("WSInfer", "Stop all running tasks?")) {
                    cancel(true);
                    e.consume();
                }
            });
        }

        @Override
        protected Void call() throws Exception {
            try {
                Platform.runLater(() -> Dialogs.showInfoNotification(getTitle(), "Requesting inference for " + model.getName()));
                WSInfer.runInference(imageData, model, progressListener);
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
