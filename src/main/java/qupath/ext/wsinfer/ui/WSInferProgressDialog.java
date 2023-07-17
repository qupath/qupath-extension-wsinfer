package qupath.ext.wsinfer.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressBar;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.ext.wsinfer.ProgressListener;

/**
 * Implementation of {@link ProgressListener} that shows a JavaFX progress dialog.
 */
class WSInferProgressDialog implements ProgressListener {

    private Dialog<Void> dialog;
    private ProgressBar progressBar = new ProgressBar();

    public WSInferProgressDialog(Window owner, EventHandler<ActionEvent> cancelHandler) {
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().setPrefWidth(200);
        dialog.setTitle("WSInfer");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        dialog.getDialogPane().setContent(progressBar);
        if (cancelHandler != null) {
            var btnCancel = (Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            btnCancel.setOnAction(cancelHandler);
        }
    }


    @Override
    public void updateProgress(String message, Double progress) {
        if (Platform.isFxApplicationThread()) {
            if (message != null)
                dialog.setHeaderText(message);
            if (progress != null) {
                progressBar.setProgress(progress);
                if (progress.doubleValue() >= 1.0)
                    dialog.hide();
                else
                    dialog.show();
            }
        } else {
            Platform.runLater(() -> updateProgress(message, progress));
        }
    }

}
