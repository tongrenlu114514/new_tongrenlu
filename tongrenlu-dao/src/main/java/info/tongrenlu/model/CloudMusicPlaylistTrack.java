package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 网易云音乐歌单曲目模型
 * 用于解析 /playlist/track/all 接口返回的歌曲数据
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudMusicPlaylistTrack {

    /**
     * 歌曲ID
     */
    private Long id;

    /**
     * 歌曲名称
     */
    private String name;

    /**
     * 专辑信息（复用现有 CloudMusicAlbum 模型）
     * 注意：此处的 CloudMusicAlbum 只包含 id, name, picUrl 字段
     */
    private CloudMusicAlbum al;

    /**
     * 艺术家列表（复用现有 CloudMusicArtist 模型）
     */
    private List<CloudMusicArtist> ar;
}
