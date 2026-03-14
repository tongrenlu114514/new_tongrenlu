package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.mapper.TagMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TagService extends ServiceImpl<TagMapper, TagBean> {

    // ==================== 缓存配置 ====================

    // 展会列表缓存（缓存所有展会数据，5分钟过期）
    private final Cache<String, List<Map<String, Object>>> eventListCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    // 展会统计数据缓存（10分钟过期）
    private final Cache<String, Map<String, Object>> eventStatsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10)
            .build();

    // 展会专辑列表缓存（3分钟过期）
    private final Cache<String, Map<String, Object>> eventAlbumsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    // 展会详情缓存（5分钟过期）
    private final Cache<Long, Map<String, Object>> eventDetailCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    // 缓存键常量
    private static final String CACHE_KEY_ALL_EVENTS = "all_events";
    private static final String CACHE_KEY_EVENT_STATS = "event_stats";

    public List<TagBean> getTagListByType(String type) {
        LambdaQueryWrapper<TagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagBean::getType, type);
        return this.list(queryWrapper);
    }

    // ==================== 展会相关方法 ====================

    /**
     * 获取所有展会及其专辑数量（带缓存）
     *
     * @return 展会列表（包含 id, tag, type, text, album_count）
     */
    public List<Map<String, Object>> getAllEventsWithAlbumCount() {
        List<Map<String, Object>> cached = eventListCache.getIfPresent(CACHE_KEY_ALL_EVENTS);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> events = this.getBaseMapper().findAllEventsWithAlbumCount();
        eventListCache.put(CACHE_KEY_ALL_EVENTS, events);
        return events;
    }

    /**
     * 搜索展会及其专辑数量
     *
     * @param keyword 搜索关键词
     * @return 匹配的展会列表
     */
    public List<Map<String, Object>> searchEventsWithAlbumCount(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return getAllEventsWithAlbumCount();
        }
        // 搜索不走缓存，直接查询
        return this.getBaseMapper().searchEventsWithAlbumCount(keyword);
    }

    /**
     * 分页获取展会及其专辑数量（带缓存）
     * 优化策略：缓存全量数据，前端分页
     *
     * @param keyword 搜索关键词（可为空）
     * @param page    页码（从1开始）
     * @param limit   每页数量
     * @param orderBy 排序方式：album_count（按专辑数）, name（按名称）
     * @return 分页结果
     */
    public Map<String, Object> getEventsWithAlbumCountPaged(String keyword, int page, int limit, String orderBy) {
        Map<String, Object> result = new HashMap<>();

        // 无关键词时，使用缓存的全量数据进行分页
        if (StringUtils.isBlank(keyword)) {
            // 获取全量数据（带缓存）
            List<Map<String, Object>> allEvents = getAllEventsWithAlbumCount();
            long total = allEvents.size();

            // 根据排序方式进行排序
            if (!"album_count".equals(orderBy)) {
                allEvents.sort((a, b) -> {
                    String nameA = (String) a.getOrDefault("tag", "");
                    String nameB = (String) b.getOrDefault("tag", "");
                    return nameA.compareToIgnoreCase(nameB);
                });
            }

            // 手动分页
            int fromIndex = (page - 1) * limit;
            int toIndex = Math.min(fromIndex + limit, allEvents.size());
            List<Map<String, Object>> events;
            if (fromIndex < allEvents.size()) {
                events = allEvents.subList(fromIndex, toIndex);
            } else {
                events = List.of();
            }

            result.put("records", events);
            result.put("total", total);
            result.put("page", page);
            result.put("limit", limit);
            result.put("totalPages", (int) Math.ceil((double) total / limit));

            return result;
        }

        // 搜索时直接查询数据库
        long offset = (long) (page - 1) * limit;
        List<Map<String, Object>> allResults = this.getBaseMapper().searchEventsWithAlbumCount(keyword);
        long total = allResults.size();

        int fromIndex = (int) offset;
        int toIndex = Math.min(fromIndex + limit, allResults.size());
        List<Map<String, Object>> events;
        if (fromIndex < allResults.size()) {
            events = allResults.subList(fromIndex, toIndex);
        } else {
            events = List.of();
        }

        result.put("records", events);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) total / limit));

        return result;
    }

    /**
     * 获取展会详情（带缓存）
     *
     * @param tagId 展会标签ID
     * @return 展会信息
     */
    public Map<String, Object> getEventDetail(Long tagId) {
        // 先查缓存
        Map<String, Object> cached = eventDetailCache.getIfPresent(tagId);
        if (cached != null) {
            return cached;
        }

        TagBean tag = this.getById(tagId);
        if (tag == null || !"event".equals(tag.getType())) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", tag.getId());
        result.put("tag", tag.getTag());
        result.put("type", tag.getType());
        result.put("text", tag.getText());

        // 获取专辑数量
        long albumCount = this.getBaseMapper().countAlbumsByEventTagId(tagId);
        result.put("album_count", albumCount);

        // 写入缓存
        eventDetailCache.put(tagId, result);

        return result;
    }

    /**
     * 获取展会的专辑列表（带缓存）
     *
     * @param tagId 展会标签ID
     * @param page  页码
     * @param limit 每页数量
     * @return 专辑列表
     */
    public Map<String, Object> getEventAlbums(Long tagId, int page, int limit) {
        String cacheKey = tagId + "_" + page + "_" + limit;

        // 先查缓存
        Map<String, Object> cached = eventAlbumsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> result = new HashMap<>();

        long offset = (long) (page - 1) * limit;
        List<Map<String, Object>> albums = this.getBaseMapper().findAlbumsByEventTagId(tagId, offset, limit);
        long total = this.getBaseMapper().countAlbumsByEventTagId(tagId);

        result.put("records", albums);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) total / limit));

        // 写入缓存
        eventAlbumsCache.put(cacheKey, result);

        return result;
    }

    /**
     * 获取展会统计数据（带缓存）
     *
     * @return 统计数据
     */
    public Map<String, Object> getEventStats() {
        // 先查缓存
        Map<String, Object> cached = eventStatsCache.getIfPresent(CACHE_KEY_EVENT_STATS);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> stats = this.getBaseMapper().getEventStats();
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("total_events", 0L);
            stats.put("total_albums", 0L);
        }

        // 写入缓存
        eventStatsCache.put(CACHE_KEY_EVENT_STATS, stats);

        return stats;
    }

    // ==================== 缓存管理方法 ====================

    /**
     * 清除展会相关缓存
     * 当展会数据更新时调用
     */
    public void clearEventCache() {
        eventListCache.invalidateAll();
        eventStatsCache.invalidateAll();
        eventAlbumsCache.invalidateAll();
        eventDetailCache.invalidateAll();
    }

}