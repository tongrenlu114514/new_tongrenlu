package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloudAlbumSearchResult {
    @JsonProperty("albums")
    private List<CloudMusicAlbum> albums;
    @JsonProperty("albumCount")
    private Long albumCount;
}
