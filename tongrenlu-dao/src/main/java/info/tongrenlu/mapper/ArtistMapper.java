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

    /**
     * 统计单个艺术家的专辑数量（已发布）。
     * 必须用 INNER JOIN：LEFT JOIN 下未发布的专辑也会留下一条 art.* 为 NULL 的行，
     * COUNT(DISTINCT rat.article_id) 会把它们算进去（详情页张数会大于列表角标）。
     */
    @Select("""
        SELECT COUNT(DISTINCT rat.article_id)
        FROM r_article_tag rat
        INNER JOIN m_article art ON art.id = rat.article_id AND art.publish_flg = '1'
        WHERE rat.tag_id = #{tagId}
        """)
    long countAlbumsByTagId(@Param("tagId") Long tagId);

    /**
     * 艺术家展示页列表：一个 m_tag 词条只出一行（m_artist 存在重复 tag_id，按 tag_id 去重），
     * 只保留至少一张已发布专辑的艺术家。
     * 代表行取 MIN(id)：OKF 导入时 artist 词条的 ref_id 通常就是这一行，便于详情命中词条。
     * 专辑数用预聚合派生表，避免逐行相关子查询；排序补 id 兜底，否则 album_count/name
     * 相同的行在 LIMIT/OFFSET 分页下会重复或漏行。
     */
    @Select("""
        <script>
        SELECT ar.id                  AS id,
               ar.name                AS name,
               ar.cloud_music_pic_url AS cloudMusicPicUrl,
               ar.tag_id              AS tagId,
               ac.album_count         AS albumCount
        FROM (
            SELECT tag_id, MIN(id) AS artist_id
            FROM m_artist
            WHERE tag_id IS NOT NULL
            GROUP BY tag_id
        ) g
        INNER JOIN m_artist ar ON ar.id = g.artist_id
        INNER JOIN (
            SELECT rat.tag_id AS tag_id, COUNT(DISTINCT rat.article_id) AS album_count
            FROM r_article_tag rat
            INNER JOIN m_article art ON art.id = rat.article_id AND art.publish_flg = '1'
            GROUP BY rat.tag_id
        ) ac ON ac.tag_id = g.tag_id
        <where>
            <if test='keyword != null'>
                AND (ar.name LIKE CONCAT('%', #{keyword}, '%')
                     OR ar.cloud_music_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
        ORDER BY ac.album_count DESC, ar.name ASC, ar.id ASC
        LIMIT #{offset}, #{limit}
        </script>
        """)
    List<Map<String, Object>> findArtistShowcasePaged(@Param("keyword") String keyword,
                                                      @Param("offset") long offset,
                                                      @Param("limit") int limit);

    /** 与 findArtistShowcasePaged 完全相同的去重口径与筛选条件。 */
    @Select("""
        <script>
        SELECT COUNT(*)
        FROM (
            SELECT tag_id, MIN(id) AS artist_id
            FROM m_artist
            WHERE tag_id IS NOT NULL
            GROUP BY tag_id
        ) g
        INNER JOIN m_artist ar ON ar.id = g.artist_id
        INNER JOIN (
            SELECT rat.tag_id AS tag_id
            FROM r_article_tag rat
            INNER JOIN m_article art ON art.id = rat.article_id AND art.publish_flg = '1'
            GROUP BY rat.tag_id
        ) ac ON ac.tag_id = g.tag_id
        <where>
            <if test='keyword != null'>
                AND (ar.name LIKE CONCAT('%', #{keyword}, '%')
                     OR ar.cloud_music_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
        </script>
        """)
    long countArtistShowcase(@Param("keyword") String keyword);

}
