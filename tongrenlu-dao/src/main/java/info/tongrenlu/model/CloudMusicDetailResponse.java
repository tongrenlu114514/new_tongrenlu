package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicDetailResponse {

    private String alName;
    private String arName;
    private String level;
    private String lyric;
    private String name;
    private String pic;
    private String size;
    private long status;
    private Object tlyric;
    private String url;

}
