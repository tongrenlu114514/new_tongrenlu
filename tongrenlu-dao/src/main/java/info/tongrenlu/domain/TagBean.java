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
import java.io.Serializable;
import java.util.Date;

@TableName(value = "m_tag", autoResultMap = true)
@JsonInclude(Include.NON_DEFAULT)
@Data
public class TagBean implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "tag", jdbcType = JdbcType.VARCHAR)
    private String tag;

    @TableField(value = "type", jdbcType = JdbcType.VARCHAR)
    private String type;

    @TableField(value = "text", jdbcType = JdbcType.VARCHAR)
    private String text;

    @TableField(value = "upd_date", jdbcType = JdbcType.TIMESTAMP)
    private Date updDate;

    @TableField(value = "del_flg", jdbcType = JdbcType.VARCHAR)
    private String delFlg;

    /** LLM 生成的 wiki 内容（markdown）；select=false 避免常规查询加载大字段 */
    @TableField(value = "wiki_content", jdbcType = JdbcType.LONGVARCHAR, select = false)
    private String wikiContent;

    /** 互联标签 JSON 数组 */
    @TableField(value = "wiki_tags", jdbcType = JdbcType.VARCHAR, select = false)
    private String wikiTags;

    /** THBWiki 社团页面链接（artist 词条） */
    @TableField(value = "wiki_source_url", jdbcType = JdbcType.VARCHAR)
    private String wikiSourceUrl;

    /** 词条生成状态：PENDING / GENERATED / NO_SOURCE / FAILED */
    @TableField(value = "wiki_status", jdbcType = JdbcType.VARCHAR)
    private String wikiStatus;

    /** 词条内容更新时间 */
    @TableField(value = "wiki_upd_date", jdbcType = JdbcType.TIMESTAMP)
    private Date wikiUpdDate;

}