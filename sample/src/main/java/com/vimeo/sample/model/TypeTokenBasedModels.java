package com.vimeo.sample.model;


import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class TypeTokenBasedModels {

    @SerializedName("videoMap")
    public Map<String, Video> videoMap;

    @SerializedName("videoList")
    public List<Video> videoList;
}
