package com.assignment.featureflagsdk.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class FlagCache {

    private final Duration ttl;
    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    public FlagCache(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(String flagKey, boolean enabled) {
        store.put(flagKey, new CacheEntry(enabled, Instant.now()));
    }

    public CacheEntry get(String flagKey) {
        return store.get(flagKey);
    }

    public boolean isExpired(CacheEntry entry) {
        return Instant.now().isAfter(entry.cachedAt().plus(ttl));
    }

    public void invalidate(String flagKey) {
        store.remove(flagKey);
    }

    public void invalidateAll() {
        store.clear();
    }

    public record CacheEntry(boolean enabled, Instant cachedAt) {}
}