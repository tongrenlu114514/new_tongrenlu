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

    /** THBWiki 社团页面链接 */
    @TableField(value = "thb_wiki_url", jdbcType = JdbcType.VARCHAR)
    private String thbWikiUrl;

    /** THBWiki 抓取状态：PENDING / MATCHED / NOT_FOUND / FETCH_FAILED */
    @TableField(value = "thb_wiki_status", jdbcType = JdbcType.VARCHAR)
    private String thbWikiStatus;

    /** LLM 生成的 wiki 内容（markdown） */
    @TableField(value = "wiki_content", jdbcType = JdbcType.LONGVARCHAR)
    private String wikiContent;

    /** 互联标签 JSON 数组：[{"tag":"...","artistId":1,"type":"artist|style"}] */
    @TableField(value = "wiki_tags", jdbcType = JdbcType.VARCHAR)
    private String wikiTags;

    /** wiki 内容更新时间 */
    @TableField(value = "wiki_upd_date", jdbcType = JdbcType.TIMESTAMP)
    private Date wikiUpdDate;

    /**
     * 已保存的专辑数量（非数据库字段，由服务层填充）
     */
    @TableField(exist = false)
    private Long albumCount;

}
