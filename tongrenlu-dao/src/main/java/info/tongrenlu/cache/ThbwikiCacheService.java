package info.tongrenlu.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import info.tongrenlu.model.ThbwikiAlbum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class ThbwikiCacheService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_CACHE_SIZE = 1000;

    private final Cache<String, ThbwikiAlbum> cache;

    public ThbwikiCacheService() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(MAX_CACHE_SIZE)
                .recordStats()
                .build();
    }

    public Optional<ThbwikiAlbum> get(String key) {
        ThbwikiAlbum album = cache.getIfPresent(key);
        if (album != null) {
            log.debug("Cache hit for key: {}", key);
        }
        return Optional.ofNullable(album);
    }

    public void put(String key, ThbwikiAlbum album) {
        cache.put(key, album);
        log.debug("Cached album: {} (key: {})", album.getName(), key);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
        log.debug("Invalidated cache for key: {}", key);
    }

    public void clear() {
        cache.invalidateAll();
        log.info("Cache cleared");
    }

    public String getStats() {
        return cache.stats().toString();
    }
}
