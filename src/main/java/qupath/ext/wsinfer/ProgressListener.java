package qupath.ext.wsinfer;

/**
 * Minimal interface required for a progress listener when running inference.
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Report a progress update.
     * @param message optional message to display
     * @param progress optional progress value between 0 and 1. If null, only the message should be used.
     */
    void updateProgress(String message, Double progress);

}
