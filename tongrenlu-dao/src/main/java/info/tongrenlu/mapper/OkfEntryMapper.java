package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.OkfEntryBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OkfEntryMapper extends BaseMapper<OkfEntryBean> {

    /** THBWiki 类词条按 (type, name) 查重。 */
    @Select("""
        SELECT * FROM m_okf_entry
        WHERE type = #{type} AND name = #{name}
        LIMIT 1
        """)
    OkfEntryBean findByTypeAndName(@Param("type") String type, @Param("name") String name);

    /** 本站迁移类词条按 (type, ref_type, ref_id) 查重。 */
    @Select("""
        SELECT * FROM m_okf_entry
        WHERE type = #{type} AND ref_type = #{refType} AND ref_id = #{refId}
        LIMIT 1
        """)
    OkfEntryBean findByRef(@Param("type") String type,
                           @Param("refType") String refType,
                           @Param("refId") Long refId);

    /** 按类型统计条数（管理端点用）。 */
    @Select("SELECT type, COUNT(*) AS cnt FROM m_okf_entry GROUP BY type")
    List<Map<String, Object>> countByType();

    /**
     * 待生成内容的条目（content 为空且非 NO_SOURCE），含正文大字段。
     * OKF 生成任务按 id 升序批量取用。del_flg 过滤在导入侧已完成，本表无该列。
     */
    @Select("""
        SELECT * FROM m_okf_entry
        WHERE (content IS NULL OR content = '')
          AND (status IS NULL OR status <> 'NO_SOURCE')
        ORDER BY id ASC
        LIMIT #{limit}
        """)
    List<OkfEntryBean> findGeneratable(@Param("limit") int limit);

    /** 待生成条目数（不含 NO_SOURCE）。 */
    @Select("""
        SELECT COUNT(*) FROM m_okf_entry
        WHERE (content IS NULL OR content = '')
          AND (status IS NULL OR status <> 'NO_SOURCE')
        """)
    long countGeneratable();
}
