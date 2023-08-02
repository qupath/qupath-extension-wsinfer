package qupath.ext.wsinfer.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.File;

/**
 * Class to store preferences associated with WSInfer.
 */
public class WSInferPrefs {

    private static final StringProperty modelDirectoryProperty = PathPrefs.createPersistentPreference(
            "wsinfer.model.dir",
            System.getProperty("user.home") + File.separator + ".cache" + File.separator + "qupath" + File.separator + "wsinfer"
    );
    private static final StringProperty deviceProperty = PathPrefs.createPersistentPreference(
            "wsinfer.device",
            "cpu"
    );
    private static final Property<Integer> numWorkersProperty = PathPrefs.createPersistentPreference(
            "wsinfer.numWorkers",
            1
    ).asObject();

    /**
     * String storing the preferred directory to cache models.
     */
    public static StringProperty modelDirectoryProperty() {
        return modelDirectoryProperty;
    }

    /**
     * String storing the preferred device to use for model inference.
     */
    public static StringProperty deviceProperty() {
        return deviceProperty;
    }

    /**
     * Integer storing the preferred number of workers for tile requests.
     */
    public static Property<Integer> numWorkersProperty() {
        return numWorkersProperty;
    }

}
