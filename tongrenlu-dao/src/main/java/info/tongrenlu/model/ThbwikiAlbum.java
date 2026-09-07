package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThbwikiAlbum {
    @JsonProperty("text")
    private String name;

    @JsonProperty("link")
    private String url;

    private List<ThbwikiTrack> tracks = new ArrayList<>();

    public void addTrack(ThbwikiTrack track) {
        this.tracks.add(track);
    }

    public List<ThbwikiTrack> getTracks() {
        return List.copyOf(this.tracks);
    }
}
