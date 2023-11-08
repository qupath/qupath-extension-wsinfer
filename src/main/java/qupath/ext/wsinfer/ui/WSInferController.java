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

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.SearchableComboBox;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ProgressListener;
import qupath.ext.wsinfer.WSInfer;
import qupath.ext.wsinfer.models.WSInferModel;
import qupath.ext.wsinfer.models.WSInferModelCollection;
import qupath.ext.wsinfer.models.WSInferModelLocal;
import qupath.ext.wsinfer.models.WSInferUtils;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.tools.WebViews;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;


/**
 * Controller for the WSInfer user interface, which is defined in wsinfer_control.fxml
 * and loaded by WSInferCommand.
 */
public class WSInferController {

    private static final Logger logger = LoggerFactory.getLogger(WSInferController.class);

    private QuPathGUI qupath;
    private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
    private MessageTextHelper messageTextHelper;

    @FXML
    private Label labelMessage;
    @FXML
    private SearchableComboBox<WSInferModel> modelChoiceBox;
    @FXML
    private Button runButton;
    @FXML
    private Button downloadButton;
    @FXML
    private Button infoButton;
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
    @FXML
    private TextField localModelDirectory;
    @FXML
    private PopOver infoPopover;

    private final static ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");

    private Stage measurementMapsStage;

    private final ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("wsinfer", true));

    private final ObjectProperty<Task<?>> pendingTask = new SimpleObjectProperty<>();

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

        configureMessageLabel();
        configureRunInferenceButton();

        configurePendingTaskProperty();
    }

    private void configureMessageLabel() {
        messageTextHelper = new MessageTextHelper();
        labelMessage.textProperty().bind(messageTextHelper.messageLabelText);
        if (messageTextHelper.hasWarning.get()) {
            labelMessage.getStyleClass().setAll("warning-message");
        } else {
            labelMessage.getStyleClass().setAll("standard-message");
        }
        messageTextHelper.hasWarning.addListener((observable, oldValue, newValue) -> {
            if (newValue)
                labelMessage.getStyleClass().setAll("warning-message");
            else
                labelMessage.getStyleClass().setAll("standard-message");
        });
    }

    /**
     * Populate the available models & configure the UI elements to select and download models.
     */
    private void configureModelChoices() {
        WSInferModelCollection models = WSInferUtils.getModelCollection();
        modelChoiceBox.getItems().setAll(models.getModels().values());
        modelChoiceBox.setConverter(new ModelStringConverter(models));
        modelChoiceBox.getSelectionModel().selectedItemProperty().addListener(
                (v, o, n) -> {
                    downloadButton.setDisable((n == null) || n.isValid());
                    infoButton.setDisable((n == null) || (!n.isValid()) || n instanceof WSInferModelLocal);
                    infoPopover.hide();
                });
    }

    private void configureAvailableDevices() {
        var available = PytorchManager.getAvailableDevices();
        deviceChoices.getItems().setAll(available);
        var selected = WSInferPrefs.deviceProperty().get();
        if (available.contains(selected)) {
            deviceChoices.getSelectionModel().select(selected);
        } else {
            deviceChoices.getSelectionModel().selectFirst();
        }
        // Don't bind property for now, since this would cause trouble if the WSInferPrefs.deviceProperty() is
        // changed elsewhere
        deviceChoices.getSelectionModel().selectedItemProperty().addListener(
                (value, oldValue, newValue) -> WSInferPrefs.deviceProperty().set(newValue));
    }

    private void configureRunInferenceButton() {
        // Disable the run button while a task is pending, or we have no model selected, or download is required
        runButton.disableProperty().bind(
                imageDataProperty.isNull()
                        .or(pendingTask.isNotNull())
                        .or(modelChoiceBox.getSelectionModel().selectedItemProperty().isNull())
                        .or(messageTextHelper.warningText.isNotEmpty())
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
        var actions = qupath.getOverlayActions();
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
        localModelDirectory.textProperty().bindBidirectional(WSInferPrefs.localDirectoryProperty());
        localModelDirectory.textProperty().addListener((v, o, n) -> configureModelChoices());
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
            Dialogs.showErrorMessage(resources.getString("title"), resources.getString("error.no-imagedata"));
            return;
        }
        // Check we have a model - and are willing to download it if needed
        var selectedModel = modelChoiceBox.getSelectionModel().getSelectedItem();
        if (selectedModel == null) {
            // Shouldn't happen
            Dialogs.showErrorMessage(resources.getString("title"), resources.getString("error.no-model"));
            return;
        }
        if (!selectedModel.isValid()) {
            if (!Dialogs.showConfirmDialog(resources.getString("title"), resources.getString("ui.model-popup"))) {
                Dialogs.showWarningNotification(resources.getString("title"), resources.getString("ui.model-not-downloaded"));
                return;
            }
        }

        if (!PytorchManager.hasPyTorchEngine()) {
            if (!Dialogs.showConfirmDialog(resources.getString("title"), resources.getString("ui.pytorch"))) {
                Dialogs.showWarningNotification(resources.getString("title"), resources.getString("ui.pytorch-popup"));
                return;
            }
        }
        submitInferenceTask(imageData, selectedModel);
    }

    private void submitInferenceTask(ImageData<BufferedImage> imageData, WSInferModel model) {
        var task = new WSInferTask(imageData, model);
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
                                resources.getString("workflow.title"),
                                WSInfer.class.getName() + ".runInference(\"" + modelName + "\")"
                        ));
    }

    private static void showDownloadingModelNotification(String modelName) {
        Dialogs.showPlainNotification(
                resources.getString("title"),
                String.format(resources.getString("ui.popup.fetching"), modelName));
    }

    private static void showModelAvailableNotification(String modelName) {
        Dialogs.showPlainNotification(
                resources.getString("title"),
                String.format(resources.getString("ui.popup.available"), modelName));
    }

    public void downloadModel() {
        var model = modelChoiceBox.getSelectionModel().getSelectedItem();
        if (model == null) {
            return;
        }
        if (model.isValid()) {
            showModelAvailableNotification(model.getName());
            return;
        }

        ForkJoinPool.commonPool().execute(() -> {
            model.removeCache();
            showDownloadingModelNotification(model.getName());
            try {
                model.downloadModel();
            } catch (IOException e) {
                Dialogs.showErrorMessage(resources.getString("title"), resources.getString("error.downloading"));
                return;
            }
            showModelAvailableNotification(model.getName());
            downloadButton.setDisable(true);
            infoButton.setDisable(model instanceof WSInferModelLocal);
        });
    }

    public void promptForModelDirectory() {
        promptToUpdateDirectory(WSInferPrefs.modelDirectoryProperty());
    }

    public void promptForLocalModelDirectory() {
        promptToUpdateDirectory(WSInferPrefs.localDirectoryProperty());
    }

    private void promptToUpdateDirectory(StringProperty dirPath) {
        var modelDirPath = dirPath.get();
        var dir = modelDirPath == null || modelDirPath.isEmpty() ? null : new File(modelDirPath);
        if (dir != null) {
            if (dir.isFile())
                dir = dir.getParentFile();
            else if (!dir.exists())
                dir = null;
        }
        var newDir = FileChoosers.promptForDirectory(
                FXUtils.getWindow(tfModelDirectory), // Get window from any node here
                resources.getString("ui.model-directory.choose-directory"),
                dir);
        if (newDir == null)
            return;
        dirPath.set(newDir.getAbsolutePath());
    }

    public void showInfo() throws IOException {
        if (infoPopover.isShowing()) {
            infoPopover.hide();
            return;
        }
        WSInferModel model = modelChoiceBox.getSelectionModel().getSelectedItem();
        Path mdFile = model.getReadMeFile().toPath();
        var doc = Parser.builder().build().parse(Files.readString(mdFile));
        WebView webView = WebViews.create(true);
        webView.getEngine().loadContent(HtmlRenderer.builder().build().render(doc));
        infoPopover.setContentNode(webView);
        infoPopover.show(infoButton);
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
        //       since that should be more robust (and also cope with language changes)
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
                if (Dialogs.showYesNoDialog(getDialogTitle(), resources.getString("ui.stop-tasks"))) {
                    cancel(true);
                    e.consume();
                }
            });
        }

        private String getDialogTitle() {
            try {
                return ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings")
                        .getString("title");
            } catch (Exception e) {
                logger.debug("Exception attempting to request title resource");
                return "WSInfer";
            }
        }

        @Override
        protected Void call() {
            try {
                // Ensure PyTorch engine is available
                if (!PytorchManager.hasPyTorchEngine()) {
                    Platform.runLater(() -> Dialogs.showInfoNotification(getDialogTitle(), resources.getString("ui.pytorch-downloading")));
                    PytorchManager.getEngineOnline();
                }
                // Ensure model is available - any prompts allowing the user to cancel
                // should have been displayed already
                if (!model.isValid()) {
                    showDownloadingModelNotification(model.getName());
                    try {
                        model.downloadModel();
                    } catch (IOException e) {
                        Platform.runLater(() -> Dialogs.showErrorMessage(resources.getString("title"), resources.getString("error.downloading")));
                        return null;
                    }
                    showModelAvailableNotification(model.getName());
                }
                // Run inference
                WSInfer.runInference(imageData, model, progressListener);
                addToHistoryWorkflow(imageData, model.getName());
            } catch (InterruptedException e) {
                Platform.runLater(() -> Dialogs.showErrorNotification(getDialogTitle(), e.getLocalizedMessage()));
            } catch (Exception e) {
                Platform.runLater(() -> Dialogs.showErrorMessage(getDialogTitle(), e.getLocalizedMessage()));
            }
            return null;
        }
    }


    /**
     * Helper class for determining which text to display in the message label.
     */
    private class MessageTextHelper {

        private final SelectedObjectCounter selectedObjectCounter;

        /**
         * Text to display a warning (because inference can't be run)
         */
        private StringBinding warningText;
        /**
         * Text to display the number of selected objects (usually when inference can be run)
         */
        private StringBinding selectedObjectText;
        /**
         * Text to display in the message label (either the warning or the selected object text)
         */
        private StringBinding messageLabelText;

        /**
         * Binding to check if the warning is empty.
         * Retained here because otherwise code that attaches a listener to {@code warningText.isEmpty()} would need to
         * retain a reference to the binding to prevent garbage collection.
         */
        private BooleanBinding hasWarning;

        MessageTextHelper() {
            this.selectedObjectCounter = new SelectedObjectCounter(imageDataProperty);
            configureMessageTextBindings();
        }

        private void configureMessageTextBindings() {
            this.warningText = createWarningTextBinding();
            this.selectedObjectText = createSelectedObjectTextBinding();
            this.messageLabelText = Bindings.createStringBinding(() -> {
                var warning = warningText.get();
                if (warning == null || warning.isEmpty())
                    return selectedObjectText.get();
                else
                    return warning;
            }, warningText, selectedObjectText);
            this.hasWarning = warningText.isEmpty().not();
        }

        private StringBinding createSelectedObjectTextBinding() {
            return Bindings.createStringBinding(this::getSelectedObjectText,
                    selectedObjectCounter.numSelectedAnnotations,
                    selectedObjectCounter.numSelectedDetections);
        }

        private String getSelectedObjectText() {
            int nAnnotations = selectedObjectCounter.numSelectedAnnotations.get();
            int nDetections = selectedObjectCounter.numSelectedDetections.get();
            if (nAnnotations == 1)
                return resources.getString("ui.selection.annotations-single");
            else if (nAnnotations > 1)
                return String.format(resources.getString("ui.selection.annotations-multiple"), nAnnotations);
            else if (nDetections == 1)
                return resources.getString("ui.selection.detections-single");
            else if (nDetections > 1)
                return String.format(resources.getString("ui.selection.detections-multiple"), nDetections);
            else
                return resources.getString("ui.selection.empty");
        }

        private StringBinding createWarningTextBinding() {
            return Bindings.createStringBinding(this::getWarningText,
                    imageDataProperty,
                    modelChoiceBox.getSelectionModel().selectedItemProperty(),
                    selectedObjectCounter.numSelectedAnnotations,
                    selectedObjectCounter.numSelectedDetections);
        }

        private String getWarningText() {
            if (imageDataProperty.get() == null)
                return resources.getString("ui.error.no-image");
            if (modelChoiceBox.getSelectionModel().isEmpty())
                return resources.getString("ui.error.no-model");
            if (selectedObjectCounter.numSelectedAnnotations.get() == 0 && selectedObjectCounter.numSelectedDetections.get() == 0)
                return resources.getString("ui.error.no-selection");
            return null;
        }
    }


    /**
     * Helper class for maintaining a count of selected annotations and detections,
     * determined from an ImageData property (whose value may change).
     * This addresses the awkwardness of attaching/detaching listeners.
     */
    private static class SelectedObjectCounter {

        private final ObjectProperty<ImageData<?>> imageDataProperty = new SimpleObjectProperty<>();

        private final PathObjectSelectionListener selectionListener = this::selectedPathObjectChanged;

        private final ObservableValue<PathObjectHierarchy> hierarchyProperty;

        private final IntegerProperty numSelectedAnnotations = new SimpleIntegerProperty();
        private final IntegerProperty numSelectedDetections = new SimpleIntegerProperty();

        SelectedObjectCounter(ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
            this.imageDataProperty.bind(imageDataProperty);
            this.hierarchyProperty = createHierarchyBinding();
            hierarchyProperty.addListener((observable, oldValue, newValue) -> updateHierarchy(oldValue, newValue));
            updateHierarchy(null, hierarchyProperty.getValue());
        }

        private ObjectBinding<PathObjectHierarchy> createHierarchyBinding() {
            return Bindings.createObjectBinding(() -> {
                        var imageData = imageDataProperty.get();
                        return imageData == null ? null : imageData.getHierarchy();
                    },
                    imageDataProperty);
        }

        private void updateHierarchy(PathObjectHierarchy oldValue, PathObjectHierarchy newValue) {
            if (oldValue == newValue)
                return;
            if (oldValue != null)
                oldValue.getSelectionModel().removePathObjectSelectionListener(selectionListener);
            if (newValue != null)
                newValue.getSelectionModel().addPathObjectSelectionListener(selectionListener);
            updateSelectedObjectCounts();
        }

        private void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
            updateSelectedObjectCounts();
        }

        private void updateSelectedObjectCounts() {
            var hierarchy = hierarchyProperty.getValue();
            if (hierarchy == null) {
                numSelectedAnnotations.set(0);
                numSelectedDetections.set(0);
            } else {
                var selected = hierarchy.getSelectionModel().getSelectedObjects();
                numSelectedAnnotations.set(
                        (int)selected
                                .stream().filter(PathObject::isAnnotation)
                                .count()
                );
                numSelectedDetections.set(
                        (int)selected
                                .stream().filter(PathObject::isDetection)
                                .count()
                );
            }
        }

    }

    private static class ModelStringConverter extends StringConverter<WSInferModel> {

        private final WSInferModelCollection models;

        private ModelStringConverter(WSInferModelCollection models) {
            Objects.requireNonNull(models, "Models cannot be null");
            this.models = models;
        }

        @Override
        public String toString(WSInferModel object) {
            for (var entry : models.getModels().entrySet()) {
                if (entry.getValue() == object)
                    return entry.getKey();
            }
            return "";
        }

        @Override
        public WSInferModel fromString(String string) {
            return models.getModels().getOrDefault(string, null);
        }
    }

}
