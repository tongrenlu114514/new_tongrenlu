package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.MusicBean;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface MusicMapper extends BaseMapper<MusicBean> {

    List<MusicBean> fetchRanking(Map<String, Object> param);

    List<MusicBean> fetchTopping(Map<String, Object> param);

}
