package qupath.ext.wsinfer.ui;

import javafx.beans.property.StringProperty;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Class to store preferences associated with WSInfer.
 */
public class WSInferPrefs {

    private static final StringProperty modelDirectoryProperty = PathPrefs.createPersistentPreference("wsinfer.model.dir", "");

    /**
     * String storing the preferred directory to cache models.
     * @return
     */
    public static StringProperty modelDirectoryProperty() {
        return modelDirectoryProperty;
    }

}
