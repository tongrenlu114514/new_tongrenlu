package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.mapper.ArticleMapper;
import org.springframework.stereotype.Service;

@Service
public class ArticleService extends ServiceImpl<ArticleMapper, ArticleBean> {
    public ArticleBean getByCloudMusicId(Long cloudMusicId) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getCloudMusicId, cloudMusicId);
        return this.getBaseMapper().selectOne(queryWrapper);
    }
}
