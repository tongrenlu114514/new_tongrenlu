package info.tongrenlu.model;

import lombok.Data;

@Data
public class ArtistAlbumRequest {
    /**
     * 歌手 id
     */
    private Long id;
    /**
     * 取出数量 , 默认为 30
     */
    private Long limit;
    /**
     * 偏移数量 , 用于分页 , 如 :( 页数 -1)*30, 其中 30 为 limit 的值 , 默认 为 0
     */
    private Long offset;
}
