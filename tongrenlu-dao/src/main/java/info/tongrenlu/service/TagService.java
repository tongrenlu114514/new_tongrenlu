package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.mapper.TagMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TagService extends ServiceImpl<TagMapper, TagBean> {

    public List<TagBean> getTagListByType(String type) {
        LambdaQueryWrapper<TagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagBean::getType, type);
        return this.list(queryWrapper);
    }

    // ==================== 展会相关方法 ====================

    /**
     * 获取所有展会及其专辑数量
     *
     * @return 展会列表（包含 id, tag, type, text, album_count）
     */
    public List<Map<String, Object>> getAllEventsWithAlbumCount() {
        return this.getBaseMapper().findAllEventsWithAlbumCount();
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
        return this.getBaseMapper().searchEventsWithAlbumCount(keyword);
    }

    /**
     * 分页获取展会及其专辑数量
     *
     * @param keyword 搜索关键词（可为空）
     * @param page    页码（从1开始）
     * @param limit   每页数量
     * @param orderBy 排序方式：album_count（按专辑数）, name（按名称）
     * @return 分页结果
     */
    public Map<String, Object> getEventsWithAlbumCountPaged(String keyword, int page, int limit, String orderBy) {
        Map<String, Object> result = new HashMap<>();

        long offset = (long) (page - 1) * limit;
        List<Map<String, Object>> events;
        long total;

        if (StringUtils.isBlank(keyword)) {
            if ("album_count".equals(orderBy)) {
                events = this.getBaseMapper().findEventsOrderByAlbumCountPaged(offset, limit);
            } else {
                events = this.getBaseMapper().findEventsOrderByNamePaged(offset, limit);
            }
            total = this.getBaseMapper().countEvents();
        } else {
            // 搜索时暂不支持排序，直接使用搜索方法
            List<Map<String, Object>> allResults = this.getBaseMapper().searchEventsWithAlbumCount(keyword);
            total = allResults.size();
            // 手动分页
            int fromIndex = (int) offset;
            int toIndex = Math.min(fromIndex + limit, allResults.size());
            if (fromIndex < allResults.size()) {
                events = allResults.subList(fromIndex, toIndex);
            } else {
                events = List.of();
            }
        }

        result.put("records", events);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) total / limit));

        return result;
    }

    /**
     * 获取展会详情
     *
     * @param tagId 展会标签ID
     * @return 展会信息
     */
    public Map<String, Object> getEventDetail(Long tagId) {
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

        return result;
    }

    /**
     * 获取展会的专辑列表
     *
     * @param tagId 展会标签ID
     * @param page  页码
     * @param limit 每页数量
     * @return 专辑列表
     */
    public Map<String, Object> getEventAlbums(Long tagId, int page, int limit) {
        Map<String, Object> result = new HashMap<>();

        long offset = (long) (page - 1) * limit;
        List<Map<String, Object>> albums = this.getBaseMapper().findAlbumsByEventTagId(tagId, offset, limit);
        long total = this.getBaseMapper().countAlbumsByEventTagId(tagId);

        result.put("records", albums);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) total / limit));

        return result;
    }

    /**
     * 获取展会统计数据
     *
     * @return 统计数据
     */
    public Map<String, Object> getEventStats() {
        Map<String, Object> stats = this.getBaseMapper().getEventStats();
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("total_events", 0L);
            stats.put("total_albums", 0L);
        }
        return stats;
    }

}