package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ArticleService extends ServiceImpl<ArticleMapper, ArticleBean> {
    public ArticleBean getByCloudMusicId(Long cloudMusicId) {
        try {
            LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleBean::getCloudMusicId, cloudMusicId);
            return this.getBaseMapper().selectOne(queryWrapper);
        } catch (Exception e) {
            log.warn("cloudMusicId = {}", cloudMusicId);
            throw e;
        }
    }
}
