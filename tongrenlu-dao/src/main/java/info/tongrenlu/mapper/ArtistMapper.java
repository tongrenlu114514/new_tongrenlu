package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ArtistMapper extends BaseMapper<ArtistBean> {

    /**
     * 获取所有艺术家及其专辑数量（优化版本：使用 JOIN 替代子查询）
     */
    @Select("""
        SELECT a.id, a.name, a.cloud_music_pic_url, a.tag_id,
               COUNT(DISTINCT rat.article_id) as album_count
        FROM m_artist a
        LEFT JOIN r_article_tag rat ON rat.tag_id = a.tag_id
        LEFT JOIN m_article art ON rat.article_id = art.id AND art.publish_flg = '1'
        WHERE a.tag_id IS NOT NULL
        GROUP BY a.id, a.name, a.cloud_music_pic_url, a.tag_id
        HAVING album_count > 0
        ORDER BY album_count DESC, a.name ASC
        """)
    List<Map<String, Object>> findAllArtistsWithAlbumCount();

    /**
     * 搜索艺术家及其专辑数量（优化版本：使用 JOIN 替代子查询）
     */
    @Select("""
        SELECT a.id, a.name, a.cloud_music_pic_url, a.tag_id,
               COUNT(DISTINCT rat.article_id) as album_count
        FROM m_artist a
        LEFT JOIN r_article_tag rat ON rat.tag_id = a.tag_id
        LEFT JOIN m_article art ON rat.article_id = art.id AND art.publish_flg = '1'
        WHERE a.tag_id IS NOT NULL
        AND a.name LIKE CONCAT('%', #{keyword}, '%')
        GROUP BY a.id, a.name, a.cloud_music_pic_url, a.tag_id
        HAVING album_count > 0
        ORDER BY album_count DESC, a.name ASC
        """)
    List<Map<String, Object>> searchArtistsWithAlbumCount(@Param("keyword") String keyword);

    /**
     * 分页获取艺术家及其专辑数量（优化版本）
     */
    @Select("""
        SELECT a.id, a.name, a.cloud_music_pic_url, a.tag_id,
               COUNT(DISTINCT rat.article_id) as album_count
        FROM m_artist a
        LEFT JOIN r_article_tag rat ON rat.tag_id = a.tag_id
        LEFT JOIN m_article art ON rat.article_id = art.id AND art.publish_flg = '1'
        WHERE a.tag_id IS NOT NULL
        GROUP BY a.id, a.name, a.cloud_music_pic_url, a.tag_id
        HAVING album_count > 0
        ORDER BY album_count DESC, a.name ASC
        LIMIT #{offset}, #{limit}
        """)
    List<Map<String, Object>> findArtistsWithAlbumCountPaged(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * 分页搜索艺术家及其专辑数量（优化版本）
     */
    @Select("""
        SELECT a.id, a.name, a.cloud_music_pic_url, a.tag_id,
               COUNT(DISTINCT rat.article_id) as album_count
        FROM m_artist a
        LEFT JOIN r_article_tag rat ON rat.tag_id = a.tag_id
        LEFT JOIN m_article art ON rat.article_id = art.id AND art.publish_flg = '1'
        WHERE a.tag_id IS NOT NULL
        AND a.name LIKE CONCAT('%', #{keyword}, '%')
        GROUP BY a.id, a.name, a.cloud_music_pic_url, a.tag_id
        HAVING album_count > 0
        ORDER BY album_count DESC, a.name ASC
        LIMIT #{offset}, #{limit}
        """)
    List<Map<String, Object>> searchArtistsWithAlbumCountPaged(@Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * 获取艺术家总数
     */
    @Select("""
        SELECT COUNT(*) FROM (
            SELECT a.id
            FROM m_artist a
            LEFT JOIN r_article_tag rat ON rat.tag_id = a.tag_id
            LEFT JOIN m_article art ON rat.article_id = art.id AND art.publish_flg = '1'
            WHERE a.tag_id IS NOT NULL
            GROUP BY a.id
            HAVING COUNT(DISTINCT rat.article_id) > 0
        ) as t
        """)
    long countArtistsWithAlbums();

    /**
     * 搜索艺术家总数
     */
    @Select("""
        SELECT COUNT(*) FROM (
            SELECT a.id
            FROM m_artist a
            LEFT JOIN r_article_tag rat ON rat.tag_id = a.tag_id
            LEFT JOIN m_article art ON rat.article_id = art.id AND art.publish_flg = '1'
            WHERE a.tag_id IS NOT NULL
            AND a.name LIKE CONCAT('%', #{keyword}, '%')
            GROUP BY a.id
            HAVING COUNT(DISTINCT rat.article_id) > 0
        ) as t
        """)
    long countSearchArtistsWithAlbums(@Param("keyword") String keyword);

}
