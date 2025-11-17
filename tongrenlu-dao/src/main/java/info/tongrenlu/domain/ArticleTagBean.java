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

@TableName(value = "r_article_tag", autoResultMap = true)
@JsonInclude(Include.NON_DEFAULT)
@Data
public class ArticleTagBean implements Serializable {

    /**
     * 
     */
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "article_id", jdbcType = JdbcType.BIGINT)
    private Long articleId;

    @TableField(value = "tag_id", jdbcType = JdbcType.BIGINT)
    private Long tagId;

    @TableField(value = "type", jdbcType = JdbcType.VARCHAR)
    private String type;

}
