package info.tongrenlu.support;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.CloudAlbumSearchResponse;
import info.tongrenlu.model.CloudAlbumSearchResult;
import info.tongrenlu.model.CloudMusicAlbum;
import info.tongrenlu.model.CloudMusicTrack;
import info.tongrenlu.service.HomeMusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Component
public class MusicAlbumParser {
    private final HomeMusicService homeMusicService;

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
                if (line.startsWith("ROOT=") || line.equals("-error.txt") || line.endsWith(".7z")) {
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

            return new MusicTrack(artistName, album.albumName, album.albumCode, album.releaseEvent, album.releaseDate, currentDisc, trackNumber, trackName, fileName);
        }

        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveArticle(MusicAlbumContext context) {
        List<MusicTrack> musicTracks = context.getMusicTracks();
        musicTracks.stream().findFirst().ifPresent(track -> {
            log.info("# 专辑名称: {}", track.getAlbumName());
            ArticleBean articleBean = toMusicBean(track);
            //album search
            if (articleBean.getPublishFlg() == null || articleBean.getPublishFlg().equals("0")) {
                CloudMusicAlbum cloudMusicAlbum = getCloudMusicAlbum(articleBean);
                if (cloudMusicAlbum != null) {
                    articleBean.setCloudMusicId(cloudMusicAlbum.getId());
                    articleBean.setCloudMusicPicUrl(cloudMusicAlbum.getPicUrl());
                    articleBean.setDescription(cloudMusicAlbum.getDescription());
                    articleBean.setPublishFlg("1");
                }
            }

            homeMusicService.insertOrUpdate(articleBean);

            if (articleBean.getCloudMusicId() != null) {
                CloudMusicAlbum cloudMusicAlbum = homeMusicService.getCloudMusicAlbumById(articleBean.getCloudMusicId());
                if (cloudMusicAlbum != null) {
                    articleBean.setCloudMusicName(cloudMusicAlbum.getName());
                    homeMusicService.insertOrUpdate(articleBean);
                    log.info("# 云音乐专辑名称: {}", cloudMusicAlbum.getName());

                    cloudMusicAlbum.getArtists().forEach(artist -> {
                        TagBean artistTag = homeMusicService.getTagByType(artist.getName(), HomeMusicService.ARTIST);
                        homeMusicService.saveArticleTag(articleBean, artistTag);
                    });

                    List<CloudMusicTrack> songs = cloudMusicAlbum.getSongs();
                    homeMusicService.saveCloudMusicTrackList(songs, articleBean);
                }
            } else {
                saveTrackBeanList(musicTracks, articleBean);
            }

            TagBean artistTag = homeMusicService.getTagByType(track.getArtistName(), HomeMusicService.ARTIST);
            homeMusicService.saveArticleTag(articleBean, artistTag);
            TagBean eventTag = homeMusicService.getTagByType(track.getReleaseEvent(), HomeMusicService.EVENT);
            homeMusicService.saveArticleTag(articleBean, eventTag);


            context.setArticleBean(articleBean);
        });
    }

    private CloudMusicAlbum getCloudMusicAlbum(ArticleBean articleBean) {
        CloudAlbumSearchResponse musicDetailResponse = homeMusicService.searchCloudMusicAlbum(new String[]{articleBean.getTitle(), articleBean.getArtist()}, 30, 0, 10);
        if (musicDetailResponse == null) {
            return null;
        }

        int code = musicDetailResponse.getCode();
        if (code != 200) {
            return null;
        }

        CloudAlbumSearchResult result = musicDetailResponse.getResult();
        List<CloudMusicAlbum> albums = result.getAlbums();
        if (albums == null || albums.isEmpty()) {
            return null;
        }

        return albums.stream()
                .filter(album -> {
                    if (articleBean.getTitle().equals(album.getName())) {
                        return true;
                    }
                    long publishTime = Long.parseLong(album.getPublishTime());
                    Date publishDate = new Date(publishTime);
                    return DateUtils.isSameDay(publishDate, articleBean.getPublishDate());
                })
                .findFirst()
                .map(CloudMusicAlbum::getId)
                .map(homeMusicService::getCloudMusicAlbumById)
                .orElse(null);
    }

    private void saveTrackBeanList(List<MusicTrack> albumTracks, ArticleBean musicBean) {
        List<TrackBean> trackBeanList = albumTracks.stream().map(track -> {
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
        }).toList();

        homeMusicService.saveTrackBeanList(trackBeanList);
    }

    private ArticleBean toMusicBean(MusicTrack track) {
        ArticleBean articleBean = Optional.ofNullable(homeMusicService.getByTitle(track.getAlbumName())).orElseGet(() -> Optional.ofNullable(homeMusicService.getByArtistAndCode(track.getArtistName(), track.getAlbumCode())).orElseGet(ArticleBean::new));
        if (articleBean.getCloudMusicId() != null) {
            articleBean.setPublishFlg("1");
        } else {
            articleBean.setDescription(track.getReleaseEvent());
            try {
                articleBean.setPublishDate(DateUtils.parseDate(track.getReleaseDate(), "yyyy.MM.dd"));
            } catch (ParseException ignored) {
            }
        }
        articleBean.setTitle(track.getAlbumName());
        articleBean.setArtist(track.getArtistName());
        articleBean.setCode(track.getAlbumCode());
        articleBean.setAccessCount(0);
        return articleBean;
    }

}