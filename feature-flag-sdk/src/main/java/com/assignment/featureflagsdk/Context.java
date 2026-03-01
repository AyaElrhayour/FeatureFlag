package com.assignment.featureflagsdk;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class Context {

    private final String userId;
    private final Map<String, String> attributes;

    private Context(String userId, Map<String, String> attributes) {
        this.userId = userId;
        this.attributes = Collections.unmodifiableMap(attributes);
    }

    public static Context user(String userId) {
        return new Context(userId, new HashMap<>());
    }

    public static Context of(String userId, Map<String, String> attributes) {
        return new Context(userId, new HashMap<>(attributes));
    }

    public static Context empty() {
        return new Context(null, new HashMap<>());
    }
}