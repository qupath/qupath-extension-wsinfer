package qupath.ext.wsinfer.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;

public class WSInferUtils {

    private static final Logger logger = LoggerFactory.getLogger(WSInferUtils.class);

    public static void downloadURLToFile(URL url, File file) {
        ReadableByteChannel readableByteChannel = null;
        try {
            readableByteChannel = Channels.newChannel(url.openStream());
        } catch (IOException e) {
            logger.error("Error opening URL {}", url, e);
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            logger.error("Error download file {}", url, e);
        }
    }

    public static String downloadJSON(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();

        HttpRequest request = builder.build();
        HttpResponse response = null;
        try {
            response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            logger.error("Error in HTTP request for URI {}", uri, e);
        }

        int code = Objects.requireNonNull(response).statusCode();

        if (code == 200) {
            return (String) response.body();
        } else if (code == 304) {
            logger.error("Error code 304 downloading {}", uri);
        }
        return null;
    }

    public static WSInferModelCollection parseModels() {
        String json = null;
        try {
            URI uri = new URI("https://huggingface.co/datasets/kaczmarj/wsinfer-model-zoo-json/raw/main/wsinfer-zoo-registry.json");
            json = downloadJSON(uri);
        } catch (URISyntaxException e) {
            logger.error("Malformed URI", e);
        }
        return GsonTools.getInstance().fromJson(json, WSInferModelCollection.class);
    }
}
