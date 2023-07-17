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
