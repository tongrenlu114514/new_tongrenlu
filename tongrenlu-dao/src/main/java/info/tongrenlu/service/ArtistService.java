package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArticleTagBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistService extends ServiceImpl<ArtistMapper, ArtistBean> {

    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;

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
        Page<ArtistBean> resultPage = this.getBaseMapper().selectPage(new PageDTO<>(page, limit), queryWrapper);
        
        // 填充每个艺人的专辑数量
        resultPage.getRecords().forEach(artist -> {
            if (artist.getTagId() != null) {
                long albumCount = this.getBaseMapper().countAlbumsByTagId(artist.getTagId());
                artist.setAlbumCount(albumCount);
            } else {
                artist.setAlbumCount(0L);
            }
        });
        
        return resultPage;
    }

    /**
     * 按网易云音乐ID查询艺人。
     * 兼容历史原因产生的重复数据：存在多条记录时不再抛
     * {@code TooManyResultsException}，而是告警并返回最早创建(id 最小)的一条。
     *
     * @param cloudMusicId 网易云音乐艺人ID
     * @return 匹配的艺人，不存在时返回 null
     */
    public ArtistBean getByCloudMusicId(long cloudMusicId) {
        LambdaQueryWrapper<ArtistBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArtistBean::getCloudMusicId, cloudMusicId)
                .orderByAsc(ArtistBean::getId);
        List<ArtistBean> list = getBaseMapper().selectList(queryWrapper);
        if (list.size() > 1) {
            log.warn("cloudMusicId = {} 存在 {} 条重复记录(建议清理 m_artist 重复数据)，本次返回 id = {}",
                    cloudMusicId, list.size(), list.get(0).getId());
        }
        return list.isEmpty() ? null : list.get(0);
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

    /**
     * 删除艺人及其关联数据
     *
     * @param artistId 艺人ID
     */
    @Transactional
    public void deleteArtist(Long artistId) {
        ArtistBean artist = this.getById(artistId);
        if (artist == null) {
            throw new RuntimeException("艺人不存在");
        }

        Long tagId = artist.getTagId();

        // 1. 删除 r_article_tag 中与艺人 tag_id 相关的关联记录
        if (tagId != null) {
            LambdaQueryWrapper<ArticleTagBean> atQueryWrapper = new LambdaQueryWrapper<>();
            atQueryWrapper.eq(ArticleTagBean::getTagId, tagId);
            articleTagMapper.delete(atQueryWrapper);

            // 2. 删除 m_tag 中的艺人标签
            tagMapper.deleteById(tagId);
        }

        // 3. 删除艺人记录
        this.removeById(artistId);
    }
}
