package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.TrackBean;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface TrackMapper   extends BaseMapper<TrackBean> {

    @Select("SELECT t.*, a.title as album_title, a.cloud_music_pic_url as cloud_music_pic_url, a.artist as album_artist " +
            "FROM m_track t " +
            "LEFT JOIN m_article a ON t.article_id = a.id " +
            "WHERE a.publish_flg = '1' " +
            "AND (t.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR t.original LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY t.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> searchTracks(@Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM m_track t " +
            "LEFT JOIN m_article a ON t.article_id = a.id " +
            "WHERE a.publish_flg = '1' " +
            "AND (t.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR t.original LIKE CONCAT('%', #{keyword}, '%'))")
    long countSearchTracks(@Param("keyword") String keyword);

}
