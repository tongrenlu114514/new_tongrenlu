package info.tongrenlu.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class AlbumDetailBean {
    
    private Long id;
    private String title;
    private String description;
    private String artist;
    private Date publishDate;
    private int accessCount;
    private int likeCount;
    private int commentCount;
    private Long cloudMusicId;
    private String cloudMusicPicUrl;
    private List<TrackBean> tracks;
}