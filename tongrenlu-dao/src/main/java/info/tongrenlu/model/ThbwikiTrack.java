package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThbwikiTrack {
    private String name;
    private String originalSource;    // 原曲出处，如 "东方Project"
    private String originalName;      // 原曲名称，如 "永夜抄"
    private String originalUrl;      // THBWiki 链接
}
