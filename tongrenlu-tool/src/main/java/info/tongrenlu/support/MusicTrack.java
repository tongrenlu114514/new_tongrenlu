package info.tongrenlu.support;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MusicTrack {
    private String artistName;
    private String albumName;
    private String albumCode;
    private String releaseEvent;
    private String releaseDate;
    private String discNumber;
    private String trackNumber;
    private String trackName;
    private String fileName;

}
