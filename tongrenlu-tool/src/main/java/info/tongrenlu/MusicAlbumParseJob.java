package info.tongrenlu;

import info.tongrenlu.support.MusicAlbumContext;
import info.tongrenlu.support.MusicAlbumParser;
import info.tongrenlu.support.MusicTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MusicAlbumParseJob implements CommandLineRunner {
    private final MusicAlbumParser musicAlbumParser;

    @Override
    public void run(String... args) throws Exception {
        String input = "E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\ls.txt";
        List<MusicTrack> tracks = musicAlbumParser.parseMusicAlbumFile(input);

        int lastProgress = 0;
        try {
            String progress = FileUtils.readFileToString(new File("E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\progress.txt"), StandardCharsets.UTF_8);
            lastProgress = Integer.parseInt(progress);
        } catch (Exception ignored) {
        }

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
                FileUtils.writeStringToFile(new File("E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\progress.txt"),
                        String.valueOf(lastProgress),
                        StandardCharsets.UTF_8);

            } catch (IOException e) {
                log.error("处理文件时出错: {}", e.getMessage(), e);
                break;
            }
        }

        log.info("处理结束，已处理：{}", albumList.size());
        FileUtils.writeStringToFile(new File("E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\progress.txt"),
                String.valueOf(0),
                StandardCharsets.UTF_8);
    }
}
