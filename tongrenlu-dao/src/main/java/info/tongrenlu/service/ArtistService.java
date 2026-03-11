package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.mapper.ArtistMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 获取所有艺术家及其专辑数量
     *
     * @return 艺术家列表（包含 id, name, cloudMusicPicUrl, albumCount）
     */
    public List<Map<String, Object>> getAllArtistsWithAlbumCount() {
        return this.getBaseMapper().findAllArtistsWithAlbumCount();
    }

    /**
     * 搜索艺术家及其专辑数量
     *
     * @param keyword 搜索关键词
     * @return 匹配的艺术家列表
     */
    public List<Map<String, Object>> searchArtistsWithAlbumCount(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return getAllArtistsWithAlbumCount();
        }
        return this.getBaseMapper().searchArtistsWithAlbumCount(keyword);
    }

    /**
     * 分页获取艺术家及其专辑数量
     *
     * @param keyword 搜索关键词（可为空）
     * @param page    页码（从1开始）
     * @param limit   每页数量
     * @return 分页结果
     */
    public Map<String, Object> getArtistsWithAlbumCountPaged(String keyword, int page, int limit) {
        Map<String, Object> result = new HashMap<>();
        
        long offset = (long) (page - 1) * limit;
        List<Map<String, Object>> artists;
        long total;
        
        if (StringUtils.isBlank(keyword)) {
            artists = this.getBaseMapper().findArtistsWithAlbumCountPaged(offset, limit);
            total = this.getBaseMapper().countArtistsWithAlbums();
        } else {
            artists = this.getBaseMapper().searchArtistsWithAlbumCountPaged(keyword, offset, limit);
            total = this.getBaseMapper().countSearchArtistsWithAlbums(keyword);
        }
        
        result.put("records", artists);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) total / limit));
        
        return result;
    }
}
