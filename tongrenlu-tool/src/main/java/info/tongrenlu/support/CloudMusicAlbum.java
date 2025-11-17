package info.tongrenlu.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicAlbum {

    private long id;
    private String name;
    private String publishTime;
    private String picUrl;
    private List<CloudMusicArtist> artists ;
    private String company ;
    private String description ;

    private List<CloudMusicTrack> songs;
}
