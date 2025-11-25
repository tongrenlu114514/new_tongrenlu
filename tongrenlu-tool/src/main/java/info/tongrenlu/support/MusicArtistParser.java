package info.tongrenlu.support;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.model.CloudMusicAlbum;
import info.tongrenlu.model.CloudMusicArtist;
import info.tongrenlu.model.CloudMusicArtistDetailResponse;
import info.tongrenlu.model.CloudMusicText;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.ArtistService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Component
public class MusicArtistParser {
    private final HomeMusicService homeMusicService;
    private final ArticleService articleService;
    private final ArtistService artistService;
    private final TagService tagService;

    public List<ArticleBean> parseMusicArtistList() {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getPublishFlg, "1");
        queryWrapper.isNotNull(ArticleBean::getCloudMusicId);
        return articleService.list(queryWrapper).stream()
                .collect(Collectors.groupingBy(ArticleBean::getArtist))
                .values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    public void saveArtist(ArticleBean articleBean) {
        CloudMusicAlbum cloudMusicAlbum = homeMusicService.getCloudMusicAlbumById(articleBean.getCloudMusicId());
        cloudMusicAlbum.getArtists().forEach(artist -> {
            log.info(artist.getName());
            ArtistBean artistBean = new ArtistBean();
            artistBean.setName(artist.getName());

            long cloudMusicArtistId = artist.getId();
            artistBean.setCloudMusicId(cloudMusicArtistId);
            artistBean.setCloudMusicName(artist.getName());
            artistBean.setCloudMusicPicUrl(artist.getPicUrl());

            String description = getDescription(cloudMusicArtistId);
            artistBean.setDescription(description);
            if (StringUtils.isNotBlank(description)) {
                log.info(description);
            }

            TagBean tagBean = getTagBean(artist.getName(), description);
            artistBean.setTagId(tagBean.getId());

            LambdaQueryWrapper<ArtistBean> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArtistBean::getCloudMusicId, artistBean.getCloudMusicId());
            ArtistBean bean = Optional.ofNullable(artistService.getBaseMapper().selectOne(queryWrapper)).orElse(artistBean);
            artistService.saveOrUpdate(bean);
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


    private String getDescription(long id) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/artist/desc")
                .queryParam("id", id)
                .build()
                .toString();
        log.info("Artist desc: {}", url);

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

}