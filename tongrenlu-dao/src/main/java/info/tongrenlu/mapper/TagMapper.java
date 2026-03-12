package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.TagBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TagMapper extends BaseMapper<TagBean> {

    /**
     * 获取所有展会标签及其专辑数量（只统计已发布的专辑）
     */
    @Select("""
        SELECT t.id, t.tag, t.type, t.text,
               COUNT(DISTINCT a.id) as album_count
        FROM m_tag t
        INNER JOIN r_article_tag rat ON t.id = rat.tag_id
        INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
        WHERE t.type = 'event'
        AND t.del_flg = '0'
        GROUP BY t.id, t.tag, t.type, t.text
        HAVING album_count > 0
        ORDER BY album_count DESC, t.tag ASC
        """)
    List<Map<String, Object>> findAllEventsWithAlbumCount();

    /**
     * 搜索展会标签及其专辑数量
     */
    @Select("""
        SELECT t.id, t.tag, t.type, t.text,
               COUNT(DISTINCT a.id) as album_count
        FROM m_tag t
        INNER JOIN r_article_tag rat ON t.id = rat.tag_id
        INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
        WHERE t.type = 'event'
        AND t.del_flg = '0'
        AND t.tag LIKE CONCAT('%', #{keyword}, '%')
        GROUP BY t.id, t.tag, t.type, t.text
        HAVING album_count > 0
        ORDER BY album_count DESC, t.tag ASC
        """)
    List<Map<String, Object>> searchEventsWithAlbumCount(@Param("keyword") String keyword);

    /**
     * 分页获取展会标签及其专辑数量（按专辑数量排序）
     */
    @Select("""
        SELECT t.id, t.tag, t.type, t.text,
               COUNT(DISTINCT a.id) as album_count
        FROM m_tag t
        INNER JOIN r_article_tag rat ON t.id = rat.tag_id
        INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
        WHERE t.type = 'event'
        AND t.del_flg = '0'
        GROUP BY t.id, t.tag, t.type, t.text
        HAVING album_count > 0
        ORDER BY album_count DESC, t.tag ASC
        LIMIT #{offset}, #{limit}
        """)
    List<Map<String, Object>> findEventsOrderByAlbumCountPaged(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * 按名称排序分页获取展会
     */
    @Select("""
        SELECT t.id, t.tag, t.type, t.text,
               COUNT(DISTINCT a.id) as album_count
        FROM m_tag t
        INNER JOIN r_article_tag rat ON t.id = rat.tag_id
        INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
        WHERE t.type = 'event'
        AND t.del_flg = '0'
        GROUP BY t.id, t.tag, t.type, t.text
        HAVING album_count > 0
        ORDER BY t.tag ASC
        LIMIT #{offset}, #{limit}
        """)
    List<Map<String, Object>> findEventsOrderByNamePaged(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * 获取展会总数
     */
    @Select("""
        SELECT COUNT(*) FROM (
            SELECT t.id
            FROM m_tag t
            INNER JOIN r_article_tag rat ON t.id = rat.tag_id
            INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
            WHERE t.type = 'event'
            AND t.del_flg = '0'
            GROUP BY t.id
            HAVING COUNT(DISTINCT a.id) > 0
        ) as tmp
        """)
    long countEvents();

    /**
     * 搜索展会数量
     */
    @Select("""
        SELECT COUNT(*) FROM (
            SELECT t.id
            FROM m_tag t
            INNER JOIN r_article_tag rat ON t.id = rat.tag_id
            INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
            WHERE t.type = 'event'
            AND t.del_flg = '0'
            AND t.tag LIKE CONCAT('%', #{keyword}, '%')
            GROUP BY t.id
            HAVING COUNT(DISTINCT a.id) > 0
        ) as tmp
        """)
    long countSearchEvents(@Param("keyword") String keyword);

    /**
     * 获取展会的专辑列表
     */
    @Select("""
        SELECT a.id, a.title, a.description, a.publish_date,
               a.cloud_music_pic_url
        FROM m_article a
        INNER JOIN r_article_tag rat ON a.id = rat.article_id
        WHERE rat.tag_id = #{tagId}
        AND a.publish_flg = '1'
        ORDER BY a.id DESC
        LIMIT #{offset}, #{limit}
        """)
    List<Map<String, Object>> findAlbumsByEventTagId(@Param("tagId") Long tagId,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);

    /**
     * 统计展会的专辑数量
     */
    @Select("""
        SELECT COUNT(*)
        FROM m_article a
        INNER JOIN r_article_tag rat ON a.id = rat.article_id
        WHERE rat.tag_id = #{tagId}
        AND a.publish_flg = '1'
        """)
    long countAlbumsByEventTagId(@Param("tagId") Long tagId);

    /**
     * 获取展会统计数据
     */
    @Select("""
        SELECT
            COUNT(DISTINCT t.id) as total_events,
            (SELECT COUNT(DISTINCT rat.article_id) FROM r_article_tag rat
             INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
             INNER JOIN m_tag t2 ON rat.tag_id = t2.id
             WHERE t2.type = 'event') as total_albums
        FROM m_tag t
        INNER JOIN r_article_tag rat ON t.id = rat.tag_id
        INNER JOIN m_article a ON rat.article_id = a.id AND a.publish_flg = '1'
        WHERE t.type = 'event'
        AND t.del_flg = '0'
        """)
    Map<String, Object> getEventStats();

}
