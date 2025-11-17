package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.ArticleTagBean;
import info.tongrenlu.domain.MusicBean;
import info.tongrenlu.domain.TagBean;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ArticleTagMapper  extends BaseMapper<ArticleTagBean> {

}
