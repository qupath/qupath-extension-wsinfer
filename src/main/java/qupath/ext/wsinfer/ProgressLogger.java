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

package qupath.ext.wsinfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ProgressListener} that sends progress messages to a logger.
 */
public class ProgressLogger implements ProgressListener {

    private Logger logger;

    public ProgressLogger(Logger logger) {
        this.logger = logger == null ? LoggerFactory.getLogger(ProgressLogger.class) : logger;
    }

    @Override
    public void updateProgress(String message, Double progress) {
        if (message == null && progress == null)
            return;
        if (message == null)
            logger.info("{}%", Math.round(progress * 100));
        else if (progress == null)
            logger.info(message);
        else
            logger.info("{}: {}%", message, Math.round(progress*100));
    }

}
