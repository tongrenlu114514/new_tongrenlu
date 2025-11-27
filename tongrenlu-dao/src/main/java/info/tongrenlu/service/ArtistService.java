package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.mapper.ArtistMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ArtistService extends ServiceImpl<ArtistMapper, ArtistBean> {

    /**
     * 分页查询歌手列表
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param limit 每页数量
     * @return 分页结果
     */
    public Page<ArtistBean> getArtistList(String keyword, int page, int limit) {
        LambdaQueryWrapper<ArtistBean> queryWrapper = new LambdaQueryWrapper<>();

        // 如果有关键词，添加搜索条件
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.like(ArtistBean::getName, keyword)
                    .or()
                    .like(ArtistBean::getCloudMusicName, keyword);
        }

        // 按ID倒序排列
        queryWrapper.orderByDesc(ArtistBean::getId);

        // 执行分页查询
        return this.getBaseMapper().selectPage(new PageDTO<>(page, limit), queryWrapper);
    }

    public ArtistBean getByCloudMusicId(long cloudMusicId) {
        LambdaQueryWrapper<ArtistBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArtistBean::getCloudMusicId, cloudMusicId);
        return getBaseMapper().selectOne(queryWrapper);
    }
}
