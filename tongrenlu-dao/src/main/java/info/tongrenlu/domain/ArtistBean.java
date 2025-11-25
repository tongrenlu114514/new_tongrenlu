package info.tongrenlu.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.util.Date;

@JsonInclude(Include.NON_DEFAULT)
@TableName(value = "m_artist", autoResultMap = true)
@Data
public class ArtistBean {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "name", jdbcType = JdbcType.VARCHAR)
    private String name;

    @TableField(value = "description", jdbcType = JdbcType.VARCHAR)
    private String description;

    @TableField(value = "cloud_music_pic_url", jdbcType = JdbcType.VARCHAR)
    private String cloudMusicPicUrl;

    @TableField(value = "cloud_music_id", jdbcType = JdbcType.INTEGER)
    private Long cloudMusicId;

    @TableField(value = "cloud_music_name", jdbcType = JdbcType.VARCHAR)
    private String cloudMusicName;

    @TableField(value = "tag_id", jdbcType = JdbcType.INTEGER)
    private Long tagId;

}
