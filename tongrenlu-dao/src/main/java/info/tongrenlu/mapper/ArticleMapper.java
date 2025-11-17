package info.tongrenlu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import info.tongrenlu.domain.ArticleBean;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArticleMapper  extends BaseMapper<ArticleBean> {

}
