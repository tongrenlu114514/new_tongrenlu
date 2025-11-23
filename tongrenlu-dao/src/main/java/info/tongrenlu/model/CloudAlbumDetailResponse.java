package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
