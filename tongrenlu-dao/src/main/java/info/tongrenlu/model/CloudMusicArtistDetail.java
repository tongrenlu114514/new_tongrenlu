package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 歌手
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicArtistDetail {
    private long albumSize;
    private List<String> alias;
    private String avatar;
    private String briefDesc;
    private String cover;
    private long id;
    private List<String> identifyTag;
    private List<String> identities;
    private long musicSize;
    private long mvSize;
    private String name;
    private List<String> transNames;
}