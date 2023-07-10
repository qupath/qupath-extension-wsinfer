package qupath.ext.wsinfer.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;
import java.util.ResourceBundle;

/**
 * Main WSInfer command.
 */
public class WSInferCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WSInferController.class);

    private QuPathGUI qupath;

    private ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");

    private Stage stage;

    public WSInferCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (stage == null) {
            try {
                stage = createStage();
            } catch (IOException e) {
                Dialogs.showErrorMessage(resources.getString("title"),
                        resources.getString("error.initializing"));
                logger.error(e.getMessage(), e);
                return;
            }
        }
        stage.show();
    }

    private Stage createStage() throws IOException {

        var url = getClass().getResource("wsinfer_control.fxml");
        if (url == null) {
            throw new IOException("Cannot find URL for WSInfer FXML");
        }

        Parent root = FXMLLoader.load(url, resources);

        Scene scene = new Scene(root);

        Stage stage = new Stage();
        stage.initOwner(qupath.getStage());
        stage.setTitle(resources.getString("title"));
        stage.setScene(scene);

        return stage;
    }

}
