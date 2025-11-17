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
@TableName(value = "music", autoResultMap = true)
@Data
public class MusicBean {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String publishFlg;

    private Date publishDate;

    private int accessCount = 0;

    private int likeCount = 0;

    private int commentCount = 0;

}
