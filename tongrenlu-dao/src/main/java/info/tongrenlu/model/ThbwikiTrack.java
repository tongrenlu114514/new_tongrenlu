package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThbwikiTrack {
    private String name;
    private String originalSource;    // 原曲出处（原作游戏），如 "东方Project"
    private String originalName;      // 拼接后的展示文本，如 "永夜抄 ／ 东方..."（多行）
    private String originalUrl;      // THBWiki 链接
    /** 结构化的原曲名列表（.ogmusic>a），与 originalName 的拼接文本并存，供直接写 tag 使用 */
    private List<String> originalSongNames;
}
