package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.OkfRawBean;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface OkfRawMapper extends BaseMapper<OkfRawBean> {

    /** 幂等查重：来源条目链接唯一。 */
    @Select("SELECT * FROM m_okf_raw WHERE url = #{url} LIMIT 1")
    OkfRawBean findByUrl(@Param("url") String url);

    /** M:N 桥表幂等关联：已存在则不重复插入。 */
    @Insert("INSERT IGNORE INTO r_okf_raw_entry (raw_id, entry_id, upd_date) " +
            "VALUES (#{rawId}, #{entryId}, #{updDate})")
    int linkRawEntry(@Param("rawId") Long rawId, @Param("entryId") Long entryId, @Param("updDate") Date updDate);

    /** 概念支撑的 raw 集合（溯源）。 */
    @Select("SELECT raw_id FROM r_okf_raw_entry WHERE entry_id = #{entryId}")
    List<Long> findRawIdsByEntryId(@Param("entryId") Long entryId);
}
