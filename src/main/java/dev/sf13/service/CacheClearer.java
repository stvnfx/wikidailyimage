package dev.sf13.service;

import jakarta.inject.Singleton;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;

import java.util.Optional;

@Singleton
public class CacheClearer {

    private final CacheManager cacheManager;

    public CacheClearer(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void clearAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            Optional<Cache> cache = cacheManager.getCache(name);
            cache.ifPresent(value -> value.invalidateAll().await().indefinitely());
        });

    }
}