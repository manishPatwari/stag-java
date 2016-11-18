package com.vimeo.sample.model;

import com.google.gson.annotations.SerializedName;

/**
 * Entity ensuring that all supported modifiers are allowed.
 */
public class AccessModifiers {

    // private modifier is not allowed

    @SerializedName("publicModifier")
    public String publicModifier;
    @SerializedName("protectedModifier")
    protected String protectedModifier;
    @SerializedName("defaultModifier")
    String defaultModifier;

}
