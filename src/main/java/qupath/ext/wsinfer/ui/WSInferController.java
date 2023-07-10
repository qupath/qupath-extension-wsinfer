package qupath.ext.wsinfer.ui;

import javafx.fxml.FXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.dialogs.Dialogs;

/**
 * Controller for the WSInfer user interface, which is defined in wsinfer_control.fxml
 * and loaded by WSInferCommand.
 */
public class WSInferController {

    private static final Logger logger = LoggerFactory.getLogger(WSInferController.class);

    private static final String TITLE = "WSInfer";

    @FXML
    private void initialize() {
        logger.info("Initializing...");
    }

    public void clickButton() {
        Dialogs.showMessageDialog(TITLE, "Hello!");
    }

}
