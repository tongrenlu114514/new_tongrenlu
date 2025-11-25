package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArtistMapper extends BaseMapper<ArtistBean> {

}
