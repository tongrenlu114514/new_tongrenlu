package info.tongrenlu.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Maps;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.model.CloudMusicAlbum;
import info.tongrenlu.model.CloudMusicArtistDetail;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.ArtistService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class MusicArtistParser {
    private final HomeMusicService homeMusicService;
    private final ArticleService articleService;
    private final ArtistService artistService;
    private final TagService tagService;
    private final Map<Long, Object> artistMap = Maps.newHashMap();

    public List<ArticleBean> parseMusicArtistList() {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getPublishFlg, "1");
        queryWrapper.isNotNull(ArticleBean::getCloudMusicId);
        return articleService.list(queryWrapper);
    }

    public void saveArtist(ArticleBean articleBean) {
        CloudMusicAlbum cloudMusicAlbum = homeMusicService.getCloudMusicAlbumById(articleBean.getCloudMusicId());
        cloudMusicAlbum.getArtists().forEach(artist -> {
            log.info(artist.getName());
            long cloudMusicArtistId = artist.getId();

            if (cloudMusicArtistId == 0) {
                return;
            }

            if(artistMap.containsKey(cloudMusicArtistId)) {
                return;
            }

            CloudMusicArtistDetail artistDetail = homeMusicService.getArtistDetail(cloudMusicArtistId);
            if(artistDetail == null) {
                return;
            }

            ArtistBean artistBean = Optional.ofNullable(artistService.getByCloudMusicId(cloudMusicArtistId))
                    .orElseGet(ArtistBean::new);
            artistBean.setCloudMusicId(cloudMusicArtistId);
            artistBean.setName(artistDetail.getName());
            artistBean.setCloudMusicName(artistDetail.getName());
            artistBean.setCloudMusicPicUrl(artistDetail.getCover());

            String description = this.homeMusicService.getDescription(cloudMusicArtistId);
            artistBean.setDescription(description);

            TagBean tagBean = getTagBean(artist.getName(), description);
            artistBean.setTagId(tagBean.getId());

            artistService.saveOrUpdate(artistBean);

            artistMap.put(cloudMusicArtistId, artistBean);
        });
    }

    public TagBean getTagBean(String tag, String text) {
        TagBean tagBean = new TagBean();
        tagBean.setType(HomeMusicService.ARTIST);
        tagBean.setTag(tag);
        tagBean.setText(text);

        LambdaQueryWrapper<TagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagBean::getTag, tagBean.getTag());
        queryWrapper.eq(TagBean::getType, tagBean.getType());
        TagBean bean = Optional.ofNullable(tagService.getBaseMapper().selectOne(queryWrapper))
                .orElse(tagBean);
        tagService.saveOrUpdate(bean);
        return bean;
    }


}