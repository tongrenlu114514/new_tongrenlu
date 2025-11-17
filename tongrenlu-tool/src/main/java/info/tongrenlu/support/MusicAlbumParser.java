package info.tongrenlu.support;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArticleTagBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.mapper.TrackMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class MusicAlbumParser {

    public static final String ARTIST = "artist";
    public static final String EVENT = "event";
    public static final String M_ARTICLE = "m_article";
    private final ArticleMapper articleMapper;
    private final TrackMapper trackMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public List<MusicTrack> parseMusicAlbumFile(String filePath) throws IOException {
        List<MusicTrack> tracks = new ArrayList<>();
        String currentArtist = "";
        String currentDisc = null;
        AlbumInfo currentAlbum = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String oline = line;
                line = line.trim();
                if (line.isEmpty()) continue;

                // 跳过ROOT行和其他非数据行
                if (line.startsWith("ROOT=") || line.equals("-error.txt") ||
                        line.endsWith(".7z")) {
                    continue;
                }

                // 解析艺术家目录（+开头的行）
                if (line.startsWith("+")) {
                    int depth = calculateDepth(oline);
                    if (depth == 0) {
                        // 第一层目录：艺术家
                        currentArtist = extractArtistName(line);
                    } else if (depth == 1) {
                        currentAlbum = parseAlbumInfo(line);
                        currentDisc = null;
                    } else if (depth == 2) {
                        currentDisc = parseDiscInfo(line);
                    }
                }

                // 解析MP3文件（-开头的行）
                if (line.startsWith("-") && line.endsWith(".mp3")) {
                    if (currentAlbum != null && !currentArtist.isEmpty()) {
                        MusicTrack track = parseTrackInfo(line, currentArtist, currentAlbum, currentDisc);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                }
            }
        }

        return tracks;
    }

    private int calculateDepth(String line) {
        int depth = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                depth++;
            } else {
                break;
            }
        }
        // 每1个空格算作一级缩进
        return depth;
    }

    private String extractArtistName(String line) {
        String content = line.replace("+", "").trim();
        // 移除可能的特殊字符
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        return content;
    }

    private AlbumInfo parseAlbumInfo(String line) {
        String content = line.replace("+", "").trim();

        // 正则表达式匹配专辑信息格式：YYYY.MM.DD [CODE] 专辑名称 [发布展会]
        Pattern pattern = Pattern.compile("(\\d{4}\\.\\d{2}\\.\\d{2})\\s+\\[([A-Z]+-\\d+)\\]\\s+(.+?)\\s+\\[(.+?)\\]");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String releaseDate = matcher.group(1);
            String albumCode = matcher.group(2);
            String albumName = matcher.group(3).trim();
            String releaseEvent = matcher.group(4);

            return new AlbumInfo(releaseDate, albumCode, albumName, releaseEvent);
        }

        return null;
    }


    private String parseDiscInfo(String line) {
        return line.replace("+", "").trim();
    }

    private MusicTrack parseTrackInfo(String line, String artistName, AlbumInfo album, String currentDisc) {
        String content = line.replace("-", "").trim();

        // 正则表达式匹配曲目信息格式：数字. 曲目名称.mp3
        Pattern pattern = Pattern.compile("(\\d+)\\.\\s*(.+?)\\.mp3");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String trackNumber = matcher.group(1);
            String trackName = matcher.group(2).trim();
            String fileName = content;

            return new MusicTrack(
                    artistName,
                    album.albumName,
                    album.albumCode,
                    album.releaseEvent,
                    album.releaseDate,
                    currentDisc,
                    trackNumber,
                    trackName,
                    fileName
            );
        }

        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveArticle(MusicAlbumContext context) {
        List<MusicTrack> musicTracks = context.getMusicTracks();
        musicTracks.stream().findFirst()
                .ifPresent(track -> {
                    log.info("# 专辑名称: {}", track.getAlbumName());
                    ArticleBean articleBean = toMusicBean(track);
                    if (articleBean.getCloudMusicId() != null) {
                        return;
                    }
                    //album search
                    CloudMusicAlbum cloudMusicAlbum = getCloudMusicAlbum(track.getAlbumName(), track.getArtistName());
                    if (cloudMusicAlbum != null) {
                        articleBean.setCloudMusicId(cloudMusicAlbum.getId());
                        articleBean.setCloudMusicPicUrl(cloudMusicAlbum.getPicUrl());
                        articleBean.setDescription(cloudMusicAlbum.getDescription());
                    }else {
                        articleBean.setPublishFlg("0");
                    }
                    articleMapper.insertOrUpdate(articleBean);

                    if (cloudMusicAlbum != null) {
                        cloudMusicAlbum.getArtists().forEach(artist -> {
                            TagBean artistTag = getTagByType(artist.getName(), ARTIST);
                            saveArticleTag(articleBean, artistTag);
                        });
                    } else {
                        TagBean artistTag = getTagByType(track.getArtistName(), ARTIST);
                        saveArticleTag(articleBean, artistTag);
                    }


                    TagBean eventTag = getTagByType(track.getReleaseEvent(), EVENT);
                    saveArticleTag(articleBean, eventTag);

                    if (cloudMusicAlbum != null) {
                        List<CloudMusicTrack> songs = cloudMusicAlbum.getSongs();
                        saveCloudMusicTrackList(songs, articleBean);
                    } else {
                        saveTrackBeanList(musicTracks, articleBean);
                    }

                    context.setArticleBean(articleBean);
                });
    }

    @SneakyThrows
    private void saveCloudMusicTrackList(List<CloudMusicTrack> songs, ArticleBean musicBean) {
        LambdaQueryWrapper<TrackBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TrackBean::getArticleId, musicBean.getId());
        trackMapper.delete(queryWrapper);

        songs.forEach(track -> {
            log.info("* 曲目名称: {}.{}", track.getNo(), track.getName());
            TrackBean trackBean = new TrackBean();
            trackBean.setArticleId(musicBean.getId());
            trackBean.setName(track.getName());
            trackBean.setCloudMusicId(track.getId());
            trackBean.setDisc(track.getCd());
            trackBean.setTrackNumber(track.getNo());
            trackBean.setOriginal(String.join(", ", track.getAlia()));

//            String url = UriComponentsBuilder.fromUriString("https://api.kxzjoker.cn/api/163_music")
//                    .queryParam("ids", track.getId())
//                    .queryParam("level", "jymaster")
//                    .queryParam("type", "json")
//                    .build()
//                    .toString();
//
//            CloudMusicDetailResponse musicDetailResponse;
//            try(HttpResponse response = HttpRequest.get(url).execute()) {
//                String json = response.body();
//                musicDetailResponse = new ObjectMapper().readValue(json, CloudMusicDetailResponse.class);
//                trackBean.setCloudMusicUrl(musicDetailResponse.getUrl());
//                trackBean.setCloudMusicLevel(musicDetailResponse.getLevel());
//                trackBean.setLyric(musicDetailResponse.getLyric());
//                trackBean.setAlbum(musicDetailResponse.getAlName());
//                trackBean.setArtist(musicDetailResponse.getArName());
//            }catch (IOException e) {
//                log.error("get {} error",url, e);
//            }
            trackMapper.insertOrUpdate(trackBean);
        });
    }

    private CloudMusicAlbum getCloudMusicAlbum(String title, String artist) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/cloudsearch")
                .queryParam("keywords", URLEncoder.encode(title, StandardCharsets.UTF_8))
                .queryParam("keywords", URLEncoder.encode(artist, StandardCharsets.UTF_8))
                .queryParam("limit", 30)
                .queryParam("offset", 0)
                .queryParam("type", 10)
                .build()
                .toString();

        CloudAlbumSearchResponse musicDetailResponse;
        try(HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            musicDetailResponse = new ObjectMapper().readValue(json, CloudAlbumSearchResponse.class);
            if (musicDetailResponse == null){
                return null;
            }

            int code = musicDetailResponse.getCode();
            if (code != 200) {
                return null;
            }

            CloudAlbumSearchResult result = musicDetailResponse.getResult();
            List<CloudMusicAlbum> albums = result.getAlbums();
            if (CollectionUtils.isEmpty(albums)) {
                return null;
            }
            return albums.stream()
                    .filter(album -> title.equals(album.getName()))
                    .findFirst()
                    .map(CloudMusicAlbum::getId)
                    .map(this::getCloudMusicAlbumById)
                    .orElse( null);
        }catch (IOException e){
            log.error("get {} error",url, e);
        }
        return null;
    }

    private CloudMusicAlbum getCloudMusicAlbumById(Long id) {
        String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/album")
                .queryParam("id", id)
                .build()
                .toString();

        CloudAlbumDetailResponse musicDetailResponse;
        try(HttpResponse response = HttpRequest.get(url).execute()) {
            String json = response.body();
            musicDetailResponse = new ObjectMapper().readValue(json, CloudAlbumDetailResponse.class);
            if (musicDetailResponse == null){
                return null;
            }
            int code = musicDetailResponse.getCode();
            if (code != 200) {
                return null;
            }

            CloudMusicAlbum album = musicDetailResponse.getAlbum();
            album.setSongs(musicDetailResponse.getSongs());
            return album;
        }catch (IOException e){
            log.error("get {} error",url, e);
        }
        return null;
    }

    private void saveTrackBeanList(List<MusicTrack> albumTracks, ArticleBean musicBean) {
        List<TrackBean> trackBeanList = albumTracks.stream()
                .map(track -> {
                    log.info("* 曲目名称: {}.{}", track.getTrackNumber(), track.getTrackName());
                    TrackBean trackBean = new TrackBean();
                    trackBean.setArticleId(musicBean.getId());
                    trackBean.setName(track.getTrackName());
                    trackBean.setAlbum(track.getAlbumName());
                    trackBean.setArtist(track.getArtistName());
                    trackBean.setDisc(track.getDiscNumber());
                    trackBean.setTrackNumber(Integer.parseInt(track.getTrackNumber()));
                    trackBean.setCloudMusicId(-1L);
                    return trackBean;
                })
                .toList();

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

    private ArticleBean toMusicBean(MusicTrack track) {
        LambdaQueryWrapper<ArticleBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleBean::getTitle, track.getAlbumName());
        ArticleBean articleBean = Optional.ofNullable(articleMapper.selectOne(queryWrapper))
                .orElseGet(ArticleBean::new);
        articleBean.setTitle(track.getAlbumName());
        articleBean.setCode(track.getAlbumCode());
        articleBean.setDescription(track.getArtistName());
        articleBean.setPublishFlg("1");
        try {
            articleBean.setPublishDate(DateUtils.parseDate(track.getReleaseDate(), "yyyy.MM.dd"));
        } catch (ParseException ignored) {
        }
        articleBean.setAccessCount(0);
        return articleBean;
    }

    private void saveArticleTag(ArticleBean musicBean, TagBean tagBean) {
        LambdaQueryWrapper<ArticleTagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleTagBean::getArticleId, musicBean.getId());
        queryWrapper.eq(ArticleTagBean::getTagId, tagBean.getId());
        ArticleTagBean articleTagBean = Optional.ofNullable(articleTagMapper.selectOne(queryWrapper))
                .orElseGet(() -> {
                    ArticleTagBean bean = new ArticleTagBean();
                    bean.setArticleId(musicBean.getId());
                    bean.setTagId(tagBean.getId());
                    return bean;
                });

        articleTagBean.setType(M_ARTICLE);
        articleTagMapper.insertOrUpdate(articleTagBean);
    }

    private TagBean getTagByType(String tag, String type) {
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

        tagMapper.insertOrUpdate(artistTag);
        return artistTag;
    }

}