package qupath.ext.wsinfer;

import com.google.gson.annotations.SerializedName;

//Class to store components of parsed hugging face json
public class WSIModel {

    private  String description;

    @SerializedName("hf_repo_id")
    private  String hfRepoId;

    @SerializedName("hf_revision")
    private  String hfRevisionhfRevision;
}
