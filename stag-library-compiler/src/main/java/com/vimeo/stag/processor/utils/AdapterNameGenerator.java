package com.vimeo.stag.processor.utils;

public final class AdapterNameGenerator {

    public static String generateName(String name) {
        name = name.replace(".", "_");
        return name;
    }
}