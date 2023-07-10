package qupath.ext.wsinfer.ui;

import javafx.beans.property.StringProperty;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.File;

/**
 * Class to store preferences associated with WSInfer.
 */
public class WSInferPrefs {

    private static final StringProperty modelDirectoryProperty = PathPrefs.createPersistentPreference(
            "wsinfer.model.dir",
            System.getProperty("user.home") + File.separator + ".qupath" + File.separator + "wsinfer"
    );
    private static final StringProperty deviceProperty = PathPrefs.createPersistentPreference(
            "wsinfer.device",
            "cpu"
    );

    /**
     * String storing the preferred directory to cache models.
     * @return
     */
    public static StringProperty modelDirectoryProperty() {
        return modelDirectoryProperty;
    }

    public static StringProperty deviceProperty() {
        return deviceProperty;
    }
}
