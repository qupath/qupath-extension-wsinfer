package qupath.ext.wsinfer;

import com.google.gson.GsonBuilder;

import qupath.lib.io.GsonTools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WSInferParsing {

//    public static void main(String[] args){
        public static void parsing(){

        try {
            var uri = new URI("https://huggingface.co/datasets/kaczmarj/wsinfer-model-zoo-json/raw/main/wsinfer-zoo-registry.json");
            var builder = HttpRequest.newBuilder(uri).GET();

            var request = builder.build();
            var response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            var headers = response.headers();
            int code = response.statusCode();

            if (code == 200) {
                var json = response.body();
                System.out.println(json);
                var modelCollection = GsonTools.getInstance().fromJson(json, ModelCollection.class);
                var models = modelCollection.getModels();
                System.out.println(models);

            } else if (code == 304) {
                System.out.println("error code 304");
            }
        }
        catch (Exception e) {
//            logger.error("Error requesting all releases: " + e.getLocalizedMessage(), e);
            System.out.println(e);
        }


    }



}
