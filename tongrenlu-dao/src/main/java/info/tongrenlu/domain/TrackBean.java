package info.tongrenlu.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;

@JsonInclude(Include.NON_DEFAULT)
@TableName(value = "m_track", autoResultMap = true)
@Data
public class TrackBean {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "article_id", jdbcType = JdbcType.INTEGER)
    private Long articleId;

    @TableField(value = "album", jdbcType = JdbcType.VARCHAR)
    private String album;

    @TableField(value = "track_number", jdbcType = JdbcType.INTEGER)
    private int trackNumber;

    @TableField(value = "name", jdbcType = JdbcType.VARCHAR)
    private String name ;

    @TableField(value = "artist", jdbcType = JdbcType.VARCHAR)
    private String artist ;

    @TableField(value = "disc", jdbcType = JdbcType.VARCHAR)
    private String disc ;

    @TableField(value = "original", jdbcType = JdbcType.VARCHAR)
    private String original ;

    @TableField(value = "instrumental", jdbcType = JdbcType.CHAR)
    private String instrumental ;

    @TableField(value = "duration", jdbcType = JdbcType.VARCHAR)
    private String duration ;

    @TableField(value = "cloud_music_id", jdbcType = JdbcType.INTEGER)
    private Long cloudMusicId;

    @TableField(value = "cloud_music_url", jdbcType = JdbcType.VARCHAR)
    private String cloudMusicUrl;

    @TableField(value = "cloud_music_level", jdbcType = JdbcType.VARCHAR)
    private String cloudMusicLevel;

    @TableField(value = "lyric", jdbcType = JdbcType.VARCHAR)
    private String lyric;
}
