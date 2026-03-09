package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 网易云音乐歌单曲目响应模型
 * 用于解析 /playlist/track/all 接口返回的数据
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicPlaylistResponse {

    /**
     * 响应状态码
     */
    private int code;

    /**
     * 歌曲列表
     */
    private List<CloudMusicPlaylistTrack> songs;
}
