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

/**
 * OKF 概念表：与 m_tag 并行的词条库（阶段 1）。
 * 六类概念：original_game / game_track / cd_track / artist / event / album。
 */
@JsonInclude(Include.NON_DEFAULT)
@TableName(value = "m_okf_entry", autoResultMap = true)
@Data
public class OkfEntryBean {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 概念类型：original_game / game_track / cd_track / artist / event / album */
    @TableField(value = "type", jdbcType = JdbcType.VARCHAR)
    private String type;

    /** 概念名（OKF title） */
    @TableField(value = "name", jdbcType = JdbcType.VARCHAR)
    private String name;

    /** 所属作品/CD（game_track、cd_track 用） */
    @TableField(value = "game", jdbcType = JdbcType.VARCHAR)
    private String game;

    /** OKF description */
    @TableField(value = "description", jdbcType = JdbcType.VARCHAR)
    private String description;

    /** markdown 正文；大字段不进常规查询 */
    @TableField(value = "content", jdbcType = JdbcType.LONGVARCHAR, select = false)
    private String content;

    /** OKF tags（沿用 wiki_tags JSON 结构） */
    @TableField(value = "tags_json", jdbcType = JdbcType.VARCHAR, select = false)
    private String tagsJson;

    /** OKF resource：THBWiki 词条页 URL / 素材页 */
    @TableField(value = "source_url", jdbcType = JdbcType.VARCHAR)
    private String sourceUrl;

    /** OKF v0.2 sources 列表 JSON（artist 的 THBWiki 社团页 + 网易云艺人描述页） */
    @TableField(value = "sources_json", jdbcType = JdbcType.VARCHAR, select = false)
    private String sourcesJson;

    /** 状态：IMPORTED / GENERATED / FAILED */
    @TableField(value = "status", jdbcType = JdbcType.VARCHAR)
    private String status;

    /** 关联本站实体类型：m_artist / m_tag / m_article */
    @TableField(value = "ref_type", jdbcType = JdbcType.VARCHAR)
    private String refType;

    /** 关联本站实体 id */
    @TableField(value = "ref_id", jdbcType = JdbcType.BIGINT)
    private Long refId;

    @TableField(value = "upd_date", jdbcType = JdbcType.TIMESTAMP)
    private Date updDate;
}
