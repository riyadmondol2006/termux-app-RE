package com.termux.app.features.packages;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PackageDetails {

    @NonNull public final String name;
    @NonNull public final Map<String, String> fields;

    PackageDetails(@NonNull String name, @NonNull Map<String, String> fields) {
        this.name = name;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public String get(String field) {
        return fields.get(field);
    }

}
