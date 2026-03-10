package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.ArticleTagBean;
import info.tongrenlu.domain.MusicBean;
import info.tongrenlu.domain.TagBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ArticleTagMapper  extends BaseMapper<ArticleTagBean> {

    @Select("""
        SELECT t.id, t.tag, t.type, t.text, COUNT(rat.id) as usage_count
        FROM m_tag t
        INNER JOIN r_article_tag rat ON t.id = rat.tag_id
        INNER JOIN m_article a ON rat.article_id = a.id
        WHERE a.publish_flg = '1'
        GROUP BY t.id, t.tag, t.type, t.text
        ORDER BY usage_count DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> getPopularTags(@Param("limit") int limit);

    @Select("SELECT t.tag FROM r_article_tag r " +
            "JOIN m_tag t ON r.tag_id = t.id " +
            "WHERE r.article_id = #{articleId} AND t.type = 'artist' " +
            "LIMIT 1")
    String findArtistNameByArticleId(@Param("articleId") Long articleId);

}
