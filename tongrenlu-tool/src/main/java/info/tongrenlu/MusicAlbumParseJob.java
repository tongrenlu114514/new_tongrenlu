package info.tongrenlu;

import info.tongrenlu.support.BatchImportPaths;
import info.tongrenlu.support.MusicAlbumContext;
import info.tongrenlu.support.MusicAlbumParser;
import info.tongrenlu.support.MusicTrack;
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
public class MusicAlbumParseJob {
    private final MusicAlbumParser musicAlbumParser;
    private final BatchImportPaths batchImportPaths;

    @GetMapping("/album/import")
    public void run() throws Exception {
        // 输入文件：默认取 classpath 资源 data/ls.txt（仓库里的同名文件不移动、不删除）
        String input = batchImportPaths.getAlbumSource();
        List<MusicTrack> tracks = musicAlbumParser.parseMusicAlbumFile(input);

        int lastProgress = batchImportPaths.readProgress("album_progress.txt");

        List<MusicAlbumContext> albumList = tracks.stream()
                .collect(Collectors.groupingBy(MusicTrack::getAlbumCode))
                .values().stream()
                .map(MusicAlbumContext::new)
                .toList();
        while (true) {
            try {
                if (lastProgress > albumList.size()) {
                    break;
                }
                albumList.stream()
                        .skip(lastProgress)
                        .findFirst()
                        .ifPresent(musicAlbumParser::saveArticle);
                lastProgress++;
                batchImportPaths.writeProgress("album_progress.txt", lastProgress);

            } catch (RuntimeException e) {
                log.error("处理文件时出错: {}", e.getMessage(), e);
                break;
            }
        }

        log.info("处理结束，已处理：{}", albumList.size());
        batchImportPaths.writeProgress("progress.txt", 0);
    }
}
