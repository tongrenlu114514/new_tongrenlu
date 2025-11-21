package info.tongrenlu.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import info.tongrenlu.model.CloudMusicAlbum;
import info.tongrenlu.model.CloudMusicTrack;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudAlbumDetailResponse {

    private int code;

    private CloudMusicAlbum album;

    private List<CloudMusicTrack> songs;

}
