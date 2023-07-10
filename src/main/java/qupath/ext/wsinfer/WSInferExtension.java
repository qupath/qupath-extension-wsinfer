package qupath.ext.wsinfer;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.wsinfer.models.WSInferParsing;
import qupath.ext.wsinfer.ui.WSInferCommand;
import qupath.ext.wsinfer.ui.WSInferPrefs;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;


/**
 * The main WSInfer extension class.
 */
public class WSInferExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(WSInferExtension.class);

	private final static String EXTENSION_NAME = "WSInfer Extension";

	private final static String EXTENSION_DESCRIPTION = "This is just a demo to show how extensions work";

	private final static Version EXTENSION_QUPATH_VERSION = Version.parse("v0.4.0");

	private boolean isInstalled = false;

	/**
	 * A 'persistent preference' - showing how to create a property that is stored whenever QuPath is closed
	 */
	private BooleanProperty enableExtensionProperty = PathPrefs.createPersistentPreference(
			"enableExtension", true);

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addPreferences(qupath);
		addMenuItems(qupath);
	}

	/**
	 * Demo showing how to add a persistent preference to the QuPath preferences pane.
	 * @param qupath
	 */
	private void addPreferences(QuPathGUI qupath) {
		qupath.getPreferencePane().addPropertyPreference(
				WSInferPrefs.modelDirectoryProperty(),
				String.class,
				"WSInfer model directory",
				EXTENSION_NAME,
				"Directory to store WSInfer cached models (leave blank to use the default)");
	}

	/**
	 * Demo showing how a new command can be added to a QuPath menu.
	 * @param qupath
	 */
	private void addMenuItems(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		MenuItem menuItem = new MenuItem("WSInfer");
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

	public static void main(String[] args){
		//Fetch and parse the HF JSON
		WSInferParsing.parsing();
	}
}
