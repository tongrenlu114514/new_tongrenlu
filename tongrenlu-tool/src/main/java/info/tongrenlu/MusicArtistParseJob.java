package info.tongrenlu;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.model.CloudMusicArtist;
import info.tongrenlu.support.BatchImportPaths;
import info.tongrenlu.support.MusicArtistParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@RestController
public class MusicArtistParseJob  {
    private final MusicArtistParser musicArtistParser;
    private final BatchImportPaths batchImportPaths;

    @GetMapping("/artist/import")
    public void run() throws Exception {
        int lastProgress = batchImportPaths.readProgress("artist_progress.txt");

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
                batchImportPaths.writeProgress("artist_progress.txt", lastProgress);

            } catch (RuntimeException e) {
                log.error("处理文件时出错: {}", e.getMessage(), e);
                break;
            }
        }

        log.info("处理结束，已处理：{}", articleBeans.size());
        batchImportPaths.writeProgress("artist_progress.txt", 0);
    }
}
