package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThbwikiAlbum {
    private String name;
    private String url;
    private List<ThbwikiTrack> tracks = new ArrayList<>();

    public void addTrack(ThbwikiTrack track) {
        this.tracks.add(track);
    }

    public List<ThbwikiTrack> getTracks() {
        return List.copyOf(this.tracks);
    }
}
