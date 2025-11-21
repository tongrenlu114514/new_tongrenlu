package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import info.tongrenlu.domain.AlbumDetailBean;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.TrackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HomeMusicService {

    private final ArticleMapper articleMapper;
    private final TrackMapper trackMapper;

    public Page<ArticleBean> getMusicTopping(final int pageSize) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        return this.articleMapper.selectPage(new PageDTO<>(1,pageSize), queryWrapper);
    }

    public Page<ArticleBean> searchMusic(String keyword, int pageNumber, int pageSize) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(ArticleBean::getCloudMusicId);
        queryWrapper.eq(ArticleBean::getPublishFlg, "1")
                .and(StringUtils.isNotBlank(keyword), wrapper ->  wrapper.like(ArticleBean::getTitle, keyword)
                        .or().like(ArticleBean::getDescription, keyword));
        queryWrapper.orderByDesc(ArticleBean::getId);
        return this.articleMapper.selectPage(new PageDTO<>(pageNumber, pageSize), queryWrapper);
    }

    public AlbumDetailBean getAlbumDetail(Long albumId) {
        // 获取专辑基本信息
        ArticleBean article = this.articleMapper.selectById(albumId);
        if (article == null) {
            return null;
        }

        // 获取专辑的曲目列表
        LambdaQueryWrapper<TrackBean> trackQueryWrapper = new LambdaQueryWrapper<>();
        trackQueryWrapper.eq(TrackBean::getArticleId, albumId);
        trackQueryWrapper.orderByAsc(TrackBean::getTrackNumber);

        List<TrackBean> tracks = this.trackMapper.selectList(trackQueryWrapper);

        // 获取点赞数和评论数
        int likeCount = getLikeCount(albumId);
        int commentCount = getCommentCount(albumId);

        // 构建专辑详情对象
        AlbumDetailBean albumDetail = new AlbumDetailBean();
        albumDetail.setId(article.getId());
        albumDetail.setTitle(article.getTitle());
        albumDetail.setDescription(article.getDescription());
        albumDetail.setPublishDate(article.getPublishDate());
        albumDetail.setAccessCount(article.getAccessCount());
        albumDetail.setLikeCount(likeCount);
        albumDetail.setCommentCount(commentCount);
        albumDetail.setCloudMusicId(article.getCloudMusicId());
        albumDetail.setCloudMusicPicUrl(article.getCloudMusicPicUrl());
        albumDetail.setTracks(tracks);
        
        // 从文章标题中提取艺术家信息（这里使用一个简单的逻辑）
        // 实际项目中可能需要更复杂的逻辑来从其他字段获取
        String title = article.getTitle();
        if (title != null && title.contains(" - ")) {
            // 假设格式为 "艺术家 - 专辑名"
            String[] parts = title.split(" - ", 2);
            if (parts.length == 2) {
                albumDetail.setArtist(parts[0].trim());
            } else {
                albumDetail.setArtist("未知艺术家");
            }
        } else {
            albumDetail.setArtist("未知艺术家");
        }

        return albumDetail;
    }
    
    // 获取点赞数的辅助方法
    private int getLikeCount(Long albumId) {
        // 这里需要根据实际的数据访问逻辑来实现
        // 暂时返回0，实际项目中需要通过Mapper查询r_like表
        return 0;
    }
    
    // 获取评论数的辅助方法
    private int getCommentCount(Long albumId) {
        // 这里需要根据实际的数据访问逻辑来实现
        // 可以通过查询m_comment表来获取
        // 暂时返回0，实际项目中需要实现
        return 0;
    }

    public TrackBean getTrackById(Long id) {
        return trackMapper.selectById(id);
    }

    public Map<String, Long> getAlbumStats() {
        // 获取已发布专辑数量 (publishFlg = "1")
        LambdaQueryWrapper<ArticleBean> publishedQuery = new LambdaQueryWrapper<>();
        publishedQuery.eq(ArticleBean::getPublishFlg, "1");
        long publishedCount = articleMapper.selectCount(publishedQuery);

        // 获取所有专辑总数
        LambdaQueryWrapper<ArticleBean> totalQuery = new LambdaQueryWrapper<>();
        long totalCount = articleMapper.selectCount(totalQuery);

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", totalCount);
        stats.put("published", publishedCount);
        return stats;
    }

    /**
     * Report an error for an album by setting its publishFlg to "0"
     * @param albumId the ID of the album to mark as having an error
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean reportAlbumError(Long albumId) {
        try {
            // Find the ArticleBean (album) by ID
            ArticleBean article = this.articleMapper.selectById(albumId);

            if (article == null) {
                // Album not found
                return false;
            }

            // Set its publishFlg to "0" to mark it as having an error
            article.setPublishFlg("0");
            article.setCloudMusicId(null);
            article.setCloudMusicPicUrl(null);

            // Update the record in the database using the articleMapper
            int updateResult = this.articleMapper.updateById(article);

            // Return true if successful (updateResult should be 1), false otherwise
            return updateResult > 0;

        } catch (Exception e) {
            // Log the error if needed
            // logger.error("Error reporting album error for albumId: {}", albumId, e);
            return false;
        }
    }

    /**
     * 获取未发布专辑列表（publishFlg=0 或 2）
     * 按id正向排序，分页显示
     *
     * @param pageNumber 页码
     * @param pageSize   每页数量
     * @return 分页的未发布专辑列表
     */
    public Page<ArticleBean> getUnpublishedAlbums(int pageNumber, int pageSize) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ArticleBean::getPublishFlg, "0")
                .orderByAsc(ArticleBean::getId);
        return this.articleMapper.selectPage(new PageDTO<>(pageNumber, pageSize), queryWrapper);
    }

    /**
     * 更新专辑信息并发布
     * 更新专辑的云音乐ID、标题、图片URL，并将publishFlg设置为1
     *
     * @param albumId          专辑ID
     * @param cloudMusicId     云音乐ID
     * @param title            专辑标题
     * @param cloudMusicPicUrl 云音乐图片URL
     * @return 操作是否成功
     */
    @Transactional
    public boolean updateAlbum(Long albumId, Long cloudMusicId, String title, String cloudMusicPicUrl) {
        try {
            ArticleBean article = this.articleMapper.selectById(albumId);

            if (article == null) {
                log.error("Album not found: {}", albumId);
                return false;
            }

            article.setCloudMusicId(cloudMusicId);
            article.setTitle(title);
            article.setCloudMusicPicUrl(cloudMusicPicUrl);
            article.setPublishFlg("1");

            int updateResult = this.articleMapper.updateById(article);

            return updateResult > 0;

        } catch (Exception e) {
            log.error("Error updating album {}", albumId, e);
            throw e;
        }
    }

    /**
     * 标记专辑为无匹配状态
     * 将publishFlg设置为2，表示该专辑在网易云音乐中没有对应的结果
     *
     * @param albumId 专辑ID
     * @return 操作是否成功
     */
    @Transactional
    public boolean markAsNoMatch(Long albumId) {
        try {
            ArticleBean article = this.articleMapper.selectById(albumId);

            if (article == null) {
                log.error("Album not found: {}", albumId);
                return false;
            }

            article.setPublishFlg("2");

            int updateResult = this.articleMapper.updateById(article);

            return updateResult > 0;

        } catch (Exception e) {
            log.error("Error marking album {} as no match", albumId, e);
            throw e;
        }
    }
}