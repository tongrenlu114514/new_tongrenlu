package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ArticleService extends ServiceImpl<ArticleMapper, ArticleBean> {

    /**
     * 按网易云音乐ID查询专辑。
     * 兼容历史原因产生的重复数据：存在多条记录时不再抛
     * {@code TooManyResultsException}，而是告警并返回最早创建(id 最小)的一条，
     * 避免歌单/专辑导入流程被重复数据中断。
     *
     * @param cloudMusicId 网易云音乐专辑ID
     * @return 匹配的专辑，不存在时返回 null
     */
    public ArticleBean getByCloudMusicId(Long cloudMusicId) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getCloudMusicId, cloudMusicId)
                .orderByAsc(ArticleBean::getId);
        List<ArticleBean> list = this.getBaseMapper().selectList(queryWrapper);
        if (list.size() > 1) {
            log.warn("cloudMusicId = {} 存在 {} 条重复记录(建议清理 m_article 重复数据)，本次返回 id = {}",
                    cloudMusicId, list.size(), list.get(0).getId());
        }
        return list.isEmpty() ? null : list.get(0);
    }
}
