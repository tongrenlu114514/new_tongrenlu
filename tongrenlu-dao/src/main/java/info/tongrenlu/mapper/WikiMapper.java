package info.tongrenlu.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Wiki 词条查询：词条主体就是 m_tag（type=artist / event），故仍以 TagBean 为实体。
 * 大字段（wiki_content / wiki_tags）在 TagBean 上 select=false，这里按需显式投影。
 * 仅放自定义查询；CRUD 统一走 TagMapper，避免同一实体出现两个 BaseMapper。
 */
@Mapper
public interface WikiMapper {

    /**
     * 分页列出词条。
     *
     * 性能要点：专辑数用「一次性预聚合派生表」LEFT JOIN 得到，不能写成逐行相关子查询——
     * ORDER BY albumCount 会让 MySQL 必须先为每个词条算完子查询再排序，LIMIT 完全无法
     * 提前终止，每次列表请求（含无限滚动）都要全量重算。派生表只扫一次 r_article_tag。
     * m_artist 可能存在重复 tag_id，用聚合派生表保证一词条一行。
     * hasContent 用 wiki_status 判断，避免为了判空去读 MEDIUMTEXT 正文列。
     * 未指定 type（百科视图）时排除 artist / event，只展示其它类型词条。
     */
    @Select("""
        <script>
        SELECT t.id AS tagId, t.tag AS name, t.type,
               t.wiki_status AS wikiStatus,
               CASE WHEN t.wiki_status = 'GENERATED' THEN 1 ELSE 0 END AS hasContent,
               a.pic_url AS coverUrl,
               a.artist_id AS artistId,
               COALESCE(ac.album_count, 0) AS albumCount
        FROM m_tag t
        LEFT JOIN (
            SELECT rat.tag_id, COUNT(DISTINCT rat.article_id) AS album_count
            FROM r_article_tag rat
            INNER JOIN m_article art ON art.id = rat.article_id AND art.publish_flg = '1'
            GROUP BY rat.tag_id
        ) ac ON ac.tag_id = t.id
        LEFT JOIN (
            SELECT tag_id, MIN(id) AS artist_id, MIN(cloud_music_pic_url) AS pic_url
            FROM m_artist
            WHERE tag_id IS NOT NULL
            GROUP BY tag_id
        ) a ON a.tag_id = t.id AND t.type = 'artist'
        WHERE t.del_flg = '0'
          <choose>
              <when test='type != null'> AND t.type = #{type} </when>
              <otherwise> AND t.type NOT IN ('artist', 'event') </otherwise>
          </choose>
          <if test='keyword != null'> AND t.tag LIKE CONCAT('%', #{keyword}, '%') </if>
        ORDER BY albumCount DESC, t.tag ASC
        LIMIT #{offset}, #{limit}
        </script>
        """)
    List<Map<String, Object>> findWikiEntries(@Param("type") String type,
                                               @Param("keyword") String keyword,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    /** 词条总数（与 findWikiEntries 相同条件：指定 type 按类型，未指定排除 artist/event）。 */
    @Select("""
        <script>
        SELECT COUNT(*)
        FROM m_tag t
        WHERE t.del_flg = '0'
          <choose>
              <when test='type != null'> AND t.type = #{type} </when>
              <otherwise> AND t.type NOT IN ('artist', 'event') </otherwise>
          </choose>
          <if test='keyword != null'> AND t.tag LIKE CONCAT('%', #{keyword}, '%') </if>
        </script>
        """)
    long countWikiEntries(@Param("type") String type, @Param("keyword") String keyword);

    /** 词条详情（含正文、标签、来源、artist 附加信息）。 */
    @Select("""
        SELECT t.id AS tagId, t.tag AS name, t.type, t.text,
               t.okf_description AS okfDescription,
               t.wiki_content AS wikiContent, t.wiki_tags AS wikiTags,
               t.wiki_source_url AS wikiSourceUrl, t.wiki_status AS wikiStatus,
               t.wiki_upd_date AS wikiUpdDate,
               a.pic_url AS coverUrl, a.cloud_music_id AS cloudMusicId,
               a.description AS description, a.artist_id AS artistId,
               (SELECT COUNT(DISTINCT rat.article_id)
                  FROM r_article_tag rat
                  INNER JOIN m_article art ON art.id = rat.article_id AND art.publish_flg = '1'
                 WHERE rat.tag_id = t.id) AS albumCount
        FROM m_tag t
        LEFT JOIN (
            SELECT tag_id, MIN(id) AS artist_id,
                   MIN(cloud_music_pic_url) AS pic_url,
                   MIN(cloud_music_id) AS cloud_music_id,
                   MIN(description) AS description
            FROM m_artist
            WHERE tag_id IS NOT NULL
            GROUP BY tag_id
        ) a ON a.tag_id = t.id AND t.type = 'artist'
        WHERE t.id = #{tagId} AND t.del_flg = '0'
        """)
    Map<String, Object> selectWikiDetail(@Param("tagId") Long tagId);

    /** 仅取 wiki 字段，供 artist-detail / 展会详情轻量读取。 */
    @Select("""
        SELECT wiki_content AS content, wiki_tags AS tags,
               wiki_source_url AS sourceUrl, wiki_status AS status
        FROM m_tag
        WHERE id = #{tagId}
        """)
    Map<String, Object> selectWikiFields(@Param("tagId") Long tagId);

    /** 词条关联专辑（服务端分页，只取卡片需要的字段）。 */
    @Select("""
        SELECT a.id, a.title, a.cloud_music_pic_url
        FROM m_article a
        INNER JOIN r_article_tag rat ON rat.article_id = a.id
        WHERE rat.tag_id = #{tagId} AND a.publish_flg = '1'
        ORDER BY a.id DESC
        LIMIT #{offset}, #{limit}
        """)
    List<Map<String, Object>> findAlbumsByTagId(@Param("tagId") Long tagId,
                                                 @Param("offset") long offset,
                                                 @Param("limit") int limit);

    /** 词条关联专辑总数。 */
    @Select("""
        SELECT COUNT(DISTINCT a.id)
        FROM m_article a
        INNER JOIN r_article_tag rat ON rat.article_id = a.id
        WHERE rat.tag_id = #{tagId} AND a.publish_flg = '1'
        """)
    long countAlbumsByTagId(@Param("tagId") Long tagId);

    /** 按词条名精确查 tagId（用于 ?tag= 深链）。 */
    @Select("""
        SELECT id FROM m_tag
        WHERE del_flg = '0' AND type IN ('artist', 'event') AND tag = #{name}
        ORDER BY id ASC LIMIT 1
        """)
    Long selectTagIdByName(@Param("name") String name);

    /**
     * OKF 导出清单：全部 wiki 词条的轻量字段（不取正文/大字段）。
     * 与 WikiService 的 4 个词条类型保持一致。
     */
    @Select("""
        SELECT t.id AS tagId, t.tag AS name, t.type,
               t.okf_description AS okfDescription,
               t.wiki_status AS wikiStatus,
               t.wiki_upd_date AS wikiUpdDate
        FROM m_tag t
        WHERE t.del_flg = '0'
          AND t.type IN ('artist', 'event', 'original_game', 'original_track')
        ORDER BY t.type, t.tag
        """)
    List<Map<String, Object>> listOkfEntries();

    /**
     * 旧 artist-wiki.html?name= 深链回退：按 m_artist.name 找到关联词条。
     * 历史数据里 m_tag.tag 与 m_artist.name 偶有不一致，避免迁移后深链失效。
     */
    @Select("""
        SELECT t.id FROM m_tag t
        INNER JOIN m_artist a ON a.tag_id = t.id
        WHERE t.del_flg = '0' AND a.name = #{name}
        ORDER BY t.id ASC LIMIT 1
        """)
    Long selectTagIdByArtistName(@Param("name") String name);
}
