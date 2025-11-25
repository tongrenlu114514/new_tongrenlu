package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicSearchAlbumResult {
    @JsonProperty("albums")
    private List<CloudMusicAlbum> albums;
    @JsonProperty("albumCount")
    private Long albumCount;
}
