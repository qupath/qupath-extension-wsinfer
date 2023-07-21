package qupath.ext.wsinfer.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ProgressListener;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;


/**
 * Implementation of {@link ProgressListener} that shows a JavaFX progress bar.
 */
class WSInferProgressDialog extends AnchorPane implements ProgressListener {

    private static final Logger logger = LoggerFactory.getLogger(WSInferProgressDialog.class);
    private final Stage stage;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;
    @FXML
    private Button btnCancel;

    public WSInferProgressDialog(Window owner, EventHandler<ActionEvent> cancelHandler) {
        URL url = getClass().getResource("progress_dialog.fxml");
        ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            logger.error("Cannot find FXML for class WSInferProgressDialog", e);
        }
        progressLabel.setLabelFor(progressBar);
        stage = new Stage();
        stage.setTitle(resources.getString("title"));
        stage.setResizable(false);
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(this));
        btnCancel.setOnAction(cancelHandler);
    }


    @Override
    public void updateProgress(String message, Double progress) {
        if (Platform.isFxApplicationThread()) {
            if (message != null)
                progressLabel.setText(message);
            if (progress != null) {
                progressBar.setProgress(progress);
                if (progress.doubleValue() >= 1.0) {
                    stage.hide();
                } else {
                    stage.show();
                }
            }
        } else {
            Platform.runLater(() -> updateProgress(message, progress));
        }
    }

}
