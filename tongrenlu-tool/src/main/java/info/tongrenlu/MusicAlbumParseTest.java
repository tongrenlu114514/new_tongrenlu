package info.tongrenlu;

import info.tongrenlu.support.MusicAlbumParser;
import info.tongrenlu.support.MusicTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MusicAlbumParseTest  {

    public static void main(String[] args) throws Exception {
        final MusicAlbumParser musicAlbumParser = new MusicAlbumParser(null, null, null,  null);
        String input = "E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\ls-sample.txt";
        List<MusicTrack> tracks = musicAlbumParser.parseMusicAlbumFile(input);

    }


}
