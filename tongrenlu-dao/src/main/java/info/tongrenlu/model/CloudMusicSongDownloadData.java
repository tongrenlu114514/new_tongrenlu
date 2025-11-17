package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicSongDownloadData {

    private long id;
    private String url;
    private long br;
    private long size;
    private String md5;
    private String level;
}
