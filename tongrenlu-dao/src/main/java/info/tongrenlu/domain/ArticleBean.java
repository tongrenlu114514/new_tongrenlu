package info.tongrenlu.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

@JsonInclude(Include.NON_DEFAULT)
@TableName(value = "m_article", autoResultMap = true)
@Data
public class ArticleBean {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "title", jdbcType = JdbcType.VARCHAR)
    private String title;

    @TableField(value = "code", jdbcType = JdbcType.VARCHAR)
    private String code;

    @TableField(value = "description", jdbcType = JdbcType.VARCHAR)
    private String description;

    @TableField(value = "publish_flg", jdbcType = JdbcType.VARCHAR)
    private String publishFlg;

    @TableField(value = "publish_date", jdbcType = JdbcType.TIMESTAMP)
    private Date publishDate;

    @TableField(value = "access_cnt", jdbcType = JdbcType.TIMESTAMP)
    private int accessCount = 0;

    @TableField(value = "cloud_music_pic_url", jdbcType = JdbcType.VARCHAR)
    private String cloudMusicPicUrl;

    @TableField(value = "cloud_music_id", jdbcType = JdbcType.VARCHAR)
    private Long cloudMusicId;

}
