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

}