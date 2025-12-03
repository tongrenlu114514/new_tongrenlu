package info.tongrenlu.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.*;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HomeMusicService {
    public static final String ARTIST = "artist";
    public static final String EVENT = "event";
    public static final String M_ARTICLE = "m_article";
    public static final int SEARCH_TYPE_ALBUM = 10;
    public static final int SEARCH_TYPE_ARTIST = 100;

    private final ArticleMapper articleMapper;
    private final TrackMapper trackMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArtistService artistService;

    public Page<ArticleBean> searchMusic(String keyword, int pageNumber, int pageSize) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(ArticleBean::getCloudMusicId);
        queryWrapper.eq(ArticleBean::getPublishFlg, "1")
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like(ArticleBean::getTitle, keyword)
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
        albumDetail.setArtist(article.getArtist());
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
     *
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
            ArticleBean articleBean = this.articleMapper.selectById(albumId);

            if (articleBean == null) {
                log.error("Album not found: {}", albumId);
                return false;
            }

            CloudMusicAlbum cloudMusicAlbum = getCloudMusicAlbumById(cloudMusicId);
            if (cloudMusicAlbum != null) {
                articleBean.setCloudMusicName(cloudMusicAlbum.getName());
                articleBean.setDescription(cloudMusicAlbum.getDescription());
                articleMapper.insertOrUpdate(articleBean);
                log.info("# 云音乐专辑名称: {}", cloudMusicAlbum.getName());

                cloudMusicAlbum.getArtists().forEach(artist -> {
                    TagBean artistTag = getTagByType(artist.getName(), ARTIST, null);
                    saveArticleTag(articleBean, artistTag.getId());
                });

                List<CloudMusicTrack> songs = cloudMusicAlbum.getSongs();
                saveCloudMusicTrackList(songs, articleBean);
            }

            articleBean.setCloudMusicId(cloudMusicId);
            articleBean.setCloudMusicPicUrl(cloudMusicPicUrl);
            articleBean.setCloudMusicName(title);
            articleBean.setPublishFlg("1");

            int updateResult = this.articleMapper.updateById(articleBean);

            return updateResult > 0;

        } catch (Exception e) {
            log.error("Error updating album {}", albumId, e);
            throw e;
        }
    }

    @SneakyThrows
    public void saveCloudMusicTrackList(List<CloudMusicTrack> songs, ArticleBean musicBean) {
        LambdaQueryWrapper<TrackBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TrackBean::getArticleId, musicBean.getId());
        trackMapper.delete(queryWrapper);

        songs.forEach(track -> {
            log.info("* 曲目名称: {}.{}, 网易云ID：{}", track.getNo(), track.getName(), track.getId());
            TrackBean trackBean = new TrackBean();
            trackBean.setArticleId(musicBean.getId());
            trackBean.setName(track.getName());
            trackBean.setCloudMusicId(track.getId());
            trackBean.setDisc(track.getCd());
            trackBean.setTrackNumber(track.getNo());
            trackBean.setOriginal(String.join(", ", track.getAlia()));
            trackMapper.insertOrUpdate(trackBean);
        });
    }

    public TagBean getTagByType(String tag, String type, String description) {
        LambdaQueryWrapper<TagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagBean::getTag, tag);
        queryWrapper.eq(TagBean::getType, type);
        TagBean artistTag = Optional.ofNullable(tagMapper.selectOne(queryWrapper))
                .orElseGet(() -> {
                    TagBean tagBean = new TagBean();
                    tagBean.setTag(tag);
                    tagBean.setType(type);
                    return tagBean;
                });
        if (StringUtils.isNotBlank(description)) {
            artistTag.setText(description);
        }
        tagMapper.insertOrUpdate(artistTag);
        return artistTag;
    }

    public CloudMusicAlbum getCloudMusicAlbumById(Long id) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/album")
                .queryParam("id", id)
                .build()
                .toString();

        CloudMusicAlbumDetailResponse musicDetailResponse;
        try (HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            musicDetailResponse = new ObjectMapper().readValue(json, CloudMusicAlbumDetailResponse.class);
            if (musicDetailResponse == null) {
                return null;
            }
            int code = musicDetailResponse.getCode();
            if (code != 200) {
                return null;
            }

            CloudMusicAlbum album = musicDetailResponse.getAlbum();
            album.setSongs(musicDetailResponse.getSongs());
            return album;
        } catch (IOException e) {
            log.error("get {} error", url, e);
        }
        return null;
    }


    public void saveArticleTag(ArticleBean musicBean, Long tagId) {
        LambdaQueryWrapper<ArticleTagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleTagBean::getArticleId, musicBean.getId());
        queryWrapper.eq(ArticleTagBean::getTagId, tagId);
        ArticleTagBean articleTagBean = Optional.ofNullable(articleTagMapper.selectOne(queryWrapper))
                .orElseGet(() -> {
                    ArticleTagBean bean = new ArticleTagBean();
                    bean.setArticleId(musicBean.getId());
                    bean.setTagId(tagId);
                    return bean;
                });

        articleTagBean.setType(M_ARTICLE);
        articleTagMapper.insertOrUpdate(articleTagBean);
    }

    /**
     * 标记专辑为无匹配状态
     * 将publishFlg设置为2，表示该专辑在网易云音乐中没有对应的结果
     *
     * @param albumId 专辑ID
     * @return 操作是否成功
     */
    @Transactional(rollbackFor = Exception.class)
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

    public void insertOrUpdate(ArticleBean articleBean) {
        articleMapper.insertOrUpdate(articleBean);
    }

    public void saveTrackBeanList(List<TrackBean> trackBeanList) {
        trackBeanList.stream()
                .map(trackBean -> {
                    LambdaQueryWrapper<TrackBean> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(TrackBean::getArticleId, trackBean.getArticleId());
                    queryWrapper.eq(StringUtils.isNotBlank(trackBean.getDisc()), TrackBean::getDisc, trackBean.getDisc());
                    queryWrapper.eq(TrackBean::getTrackNumber, trackBean.getTrackNumber());
                    List<TrackBean> trackBeans = trackMapper.selectList(queryWrapper);
                    if (trackBeans.size() != 1) {
                        trackMapper.delete(queryWrapper);
                    }
                    return trackBeans.stream().findFirst().orElse(trackBean);
                })
                .forEach(trackMapper::insertOrUpdate);
    }

    public ArticleBean getByTitle(String title) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getTitle, title);
        return articleMapper.selectOne(queryWrapper);
    }

    public ArticleBean getByArtistAndCode(String artistName, String code) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getArtist, artistName);
        queryWrapper.eq(ArticleBean::getCode, code);
        return articleMapper.selectOne(queryWrapper);
    }

    public CloudMusicSearchAlbumResponse searchCloudMusicAlbum(String[] keywords, int limit, int offset) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/cloudsearch")
                .queryParam("keywords", Arrays.stream(keywords).map(keyword -> URLEncoder.encode(keyword, StandardCharsets.UTF_8)).toArray())
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("type", SEARCH_TYPE_ALBUM)
                .build()
                .toString();
        log.info("Searching cloud music: {}", url);

        try (HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            return new ObjectMapper().readValue(json, CloudMusicSearchAlbumResponse.class);
        } catch (IOException e) {
            log.error("get {} error", url, e);
        }
        return null;
    }

    public CloudMusicSearchArtistResponse searchCloudMusicArtist(String[] keywords, int limit, int offset) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/cloudsearch")
                .queryParam("keywords", Arrays.stream(keywords).map(keyword -> URLEncoder.encode(keyword, StandardCharsets.UTF_8)).toArray())
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("type", SEARCH_TYPE_ARTIST)
                .build()
                .toString();
        log.info("Searching cloud music: {}", url);

        try (HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            return new ObjectMapper().readValue(json, CloudMusicSearchArtistResponse.class);
        } catch (IOException e) {
            log.error("get {} error", url, e);
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveCloudMusicAlbum(Long cloudMusicId) {
        CloudMusicAlbum cloudMusicAlbum = this.getCloudMusicAlbumById(cloudMusicId);
        if (cloudMusicAlbum == null) {
            return;
        }

        ArticleBean articleBean = new ArticleBean();
        articleBean.setTitle(cloudMusicAlbum.getName());
        articleBean.setAccessCount(0);
        articleBean.setCloudMusicName(cloudMusicAlbum.getName());
        articleBean.setCloudMusicId(cloudMusicAlbum.getId());
        articleBean.setCloudMusicPicUrl(cloudMusicAlbum.getPicUrl());
        articleBean.setDescription(cloudMusicAlbum.getDescription());
        articleBean.setPublishFlg("1");

        this.insertOrUpdate(articleBean);

        cloudMusicAlbum.getArtists().forEach(artist -> {
            this.saveArtist(artist);
            TagBean artistTag = this.getTagByType(artist.getName(), HomeMusicService.ARTIST, null);
            this.saveArticleTag(articleBean, artistTag.getId());
        });

        List<CloudMusicTrack> songs = cloudMusicAlbum.getSongs();
        this.saveCloudMusicTrackList(songs, articleBean);

        log.info("save cloud music album: {}", cloudMusicAlbum.getName());
    }

    public void saveArtist(CloudMusicArtist artist) {
        ArtistBean artistBean = new ArtistBean();
        artistBean.setName(artist.getName());

        long cloudMusicArtistId = artist.getId();
        artistBean.setCloudMusicId(cloudMusicArtistId);
        artistBean.setCloudMusicName(artist.getName());
        artistBean.setCloudMusicPicUrl(artist.getPicUrl());

        String description = getDescription(cloudMusicArtistId);
        artistBean.setDescription(description);

        TagBean tagBean = getTagByType(artist.getName(), HomeMusicService.ARTIST, description);
        artistBean.setTagId(tagBean.getId());

        LambdaQueryWrapper<ArtistBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArtistBean::getCloudMusicId, artistBean.getCloudMusicId());
        ArtistBean bean = Optional.ofNullable(artistService.getBaseMapper().selectOne(queryWrapper)).orElse(artistBean);
        artistService.saveOrUpdate(bean);
    }

    public String getDescription(long id) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/artist/desc")
                .queryParam("id", id)
                .build()
                .toString();
        log.info("Artist desc: {}", url);

        try (HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            CloudMusicArtistDescResponse artistDetailResponse = new ObjectMapper().readValue(json, CloudMusicArtistDescResponse.class);
            if (artistDetailResponse == null) {
                return null;
            }
            int code = artistDetailResponse.getCode();
            if (code != 200) {
                return null;
            }


            CloudMusicText brief = new CloudMusicText();
            brief.setTi("简介");
            brief.setText(artistDetailResponse.getBriefDesc());

            StringBuilder markDownBuilder = new StringBuilder();
            Stream.concat(Stream.of(brief), artistDetailResponse.getIntroduction().stream())
                    .filter(desc -> StringUtils.isNotBlank(desc.getTi()))
                    .filter(desc -> StringUtils.isNotBlank(desc.getText()))
                    .forEach(desc -> {
                        markDownBuilder.append("# ").append(desc.getTi()).append("\n");
                        markDownBuilder.append(" ").append(desc.getText()).append("\n\n");
                    });
            return markDownBuilder.toString();
        } catch (IOException e) {
            log.error("get {} error", url, e);
        }
        return null;
    }

    public CloudMusicArtistDetail getArtistDetail(long id) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/artist/detail")
                .queryParam("id", id)
                .build()
                .toString();
        log.info("Artist detail: {}", url);

        try (HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            CloudMusicArtistDetailResponse artistDetailResponse = new ObjectMapper().readValue(json, CloudMusicArtistDetailResponse.class);
            if (artistDetailResponse == null) {
                return null;
            }
            int code = artistDetailResponse.getCode();
            if (code != 200) {
                return null;
            }

            CloudMusicArtistDetailData data = artistDetailResponse.getData();
            if (data == null) {
                return null;
            }
            return data.getArtist();
        } catch (IOException e) {
            log.error("get {} error", url, e);
        }
        return null;
    }

    /**
     * 随机获取一个已发布的专辑
     *
     * @return 随机专辑详情，如果没有已发布专辑则返回null
     */
    public AlbumDetailBean getRandomAlbum() {
        // 查询已发布的专辑总数
        LambdaQueryWrapper<ArticleBean> countQuery = new LambdaQueryWrapper<>();
        countQuery.eq(ArticleBean::getPublishFlg, "1");
        long publishedCount = articleMapper.selectCount(countQuery);

        if (publishedCount == 0) {
            return null;
        }

        // 随机选择一个偏移量
        int randomOffset = new Random().nextInt((int) publishedCount);

        // 查询该随机位置的一个专辑
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getPublishFlg, "1");
        queryWrapper.orderByAsc(ArticleBean::getId);
        queryWrapper.last("LIMIT " + randomOffset + ", 1");

        ArticleBean randomArticle = articleMapper.selectOne(queryWrapper);

        if (randomArticle == null) {
            return null;
        }

        return getAlbumDetail(randomArticle.getId());
    }
}