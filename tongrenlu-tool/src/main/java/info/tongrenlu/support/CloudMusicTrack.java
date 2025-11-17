package info.tongrenlu.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicTrack {

    private List<CloudMusicArtist> ar;
    private CloudMusicAlbum al;
    private Integer no;
    private String name;
    private long id;
    private List<String> alia;
    private String cd;
}
