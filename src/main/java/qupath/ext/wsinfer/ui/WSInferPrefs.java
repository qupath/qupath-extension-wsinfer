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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.UserDirectoryManager;

import java.io.File;
import java.nio.file.Paths;
import java.nio.file.Path;

/**
 * Class to store preferences associated with WSInfer.
 */
public class WSInferPrefs {

    private static final StringProperty modelDirectoryProperty = PathPrefs.createPersistentPreference(
            "wsinfer.model.dir",
            Paths.get(getUserDir().toString(),"wsinfer").toString()
    );

    private static final StringProperty deviceProperty = PathPrefs.createPersistentPreference(
            "wsinfer.device",
            "cpu"
    );

    private static final Property<Integer> numWorkersProperty = PathPrefs.createPersistentPreference(
            "wsinfer.numWorkers",
            1
    ).asObject();
    private static StringProperty localDirectoryProperty = PathPrefs.createPersistentPreference(
            "wsinfer.localDirectory",
            null);

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

    /**
     * String storing the preferred directory for user-supplied model folders.
     */
    public static StringProperty localDirectoryProperty() {
        return localDirectoryProperty;
    }

    private static Path getUserDir() {
        Path userPath = UserDirectoryManager.getInstance().getUserPath();
        Path cachePath = Paths.get(System.getProperty("user.dir"), ".cache", "QuPath");
        return userPath == null || userPath.toString().isEmpty() ?  cachePath : userPath;
    }


}
