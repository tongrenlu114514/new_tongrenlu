package info.tongrenlu;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.model.CloudMusicArtist;
import info.tongrenlu.support.MusicAlbumContext;
import info.tongrenlu.support.MusicAlbumParser;
import info.tongrenlu.support.MusicArtistParser;
import info.tongrenlu.support.MusicTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@RestController
public class MusicArtistParseJob  {
    private final MusicArtistParser musicArtistParser;

    @GetMapping("/artist/import")
    public void run() throws Exception {
        int lastProgress = 0;
        try {
            String progress = FileUtils.readFileToString(new File("E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\artist_progress.txt"), StandardCharsets.UTF_8);
            lastProgress = Integer.parseInt(progress);
        } catch (Exception ignored) {
        }

        List<ArticleBean> articleBeans = musicArtistParser.parseMusicArtistList();
        while (true) {
            try {
                if (lastProgress > articleBeans.size()) {
                    break;
                }
                articleBeans.stream()
                        .skip(lastProgress)
                        .findFirst()
                        .ifPresent(musicArtistParser::saveArtist);
                lastProgress++;
                FileUtils.writeStringToFile(new File("E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\artist_progress.txt"),
                        String.valueOf(lastProgress),
                        StandardCharsets.UTF_8);

            } catch (IOException e) {
                log.error("处理文件时出错: {}", e.getMessage(), e);
                break;
            }
        }

        log.info("处理结束，已处理：{}", articleBeans.size());
        FileUtils.writeStringToFile(new File("E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\artist_progress.txt"),
                String.valueOf(0),
                StandardCharsets.UTF_8);
    }
}
