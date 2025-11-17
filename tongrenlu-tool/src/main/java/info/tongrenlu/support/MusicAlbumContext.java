package info.tongrenlu.support;

import info.tongrenlu.domain.ArticleBean;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class MusicAlbumContext {

    private final List<MusicTrack> musicTracks;

    private ArticleBean articleBean;

}
