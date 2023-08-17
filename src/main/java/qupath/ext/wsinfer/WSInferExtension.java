package qupath.ext.wsinfer;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.ui.WSInferCommand;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.ResourceBundle;

/**
 * QuPath extension to run patch-based deep learning inference with WSInfer.
 * See https://wsinfer.readthedocs.io for more info.
 */
public class WSInferExtension implements QuPathExtension {
	private final static ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.wsinfer.ui.strings");
	
	private final static Logger logger = LoggerFactory.getLogger(WSInferExtension.class);

	private final static String EXTENSION_NAME = resources.getString("extension.title");

	private final static String EXTENSION_DESCRIPTION = resources.getString("extension.description");

	private final static Version EXTENSION_QUPATH_VERSION = Version.parse(resources.getString("extension.version"));

	private boolean isInstalled = false;

	private final BooleanProperty enableExtensionProperty = PathPrefs.createPersistentPreference(
			"enableExtension", true);



	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addMenuItems(qupath);
	}

	private void addMenuItems(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		MenuItem menuItem = new MenuItem(resources.getString("title"));
		WSInferCommand command = new WSInferCommand(qupath);
		menuItem.setOnAction(e -> command.run());
		menuItem.disableProperty().bind(enableExtensionProperty.not());
		menu.getItems().add(menuItem);
	}
	
	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}

}
