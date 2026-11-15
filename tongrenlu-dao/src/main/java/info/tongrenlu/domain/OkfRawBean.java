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
 * OKF 原始数据表：来源页上的每条原始记录（原曲列表页里某作品表的一行、CD 曲目列表里
 * 某张 CD 的一首曲目、本站迁移的一条实体字段）。raw 与 entry 是 M:N
 * （桥表 {@link OkfRawEntryBean}）：一条 raw 可派生多个概念，一个概念由多条 raw 支撑。
 */
@JsonInclude(Include.NON_DEFAULT)
@TableName(value = "m_okf_raw", autoResultMap = true)
@Data
public class OkfRawBean {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 来源标识：tracklist / cd_tracklist / local */
    @TableField(value = "source", jdbcType = JdbcType.VARCHAR)
    private String source;

    /** 来源页内条目链接（唯一标识，幂等导入按此查重） */
    @TableField(value = "url", jdbcType = JdbcType.VARCHAR)
    private String url;

    /** 结构化原始字段（曲号/日英文名/时长/labels 等） */
    @TableField(value = "raw_json", jdbcType = JdbcType.LONGVARCHAR)
    private String rawJson;

    @TableField(value = "upd_date", jdbcType = JdbcType.TIMESTAMP)
    private Date updDate;
}
