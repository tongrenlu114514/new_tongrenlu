package info.tongrenlu;

import info.tongrenlu.support.PlaylistProgressStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 只读查看某歌单的失败清单（补导之前先看清楚要补什么）。
 *
 * <p>返回内容与进度文件里的清单一致：专辑 ID、失败类型、失败原因、记录时间、已失败次数。
 * 本端点不改任何状态。
 */
@RequiredArgsConstructor
@Slf4j
@RestController
public class PlaylistFailedController {

    private final PlaylistProgressStore progressStore;

    @GetMapping("/playlist/failed")
    public Map<String, Object> failedAlbums(@RequestParam Long playlistId) {
        PlaylistProgressStore.PlaylistProgress progress = progressStore.read(playlistId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("playlistId", playlistId);
        body.put("index", progress.index());
        body.put("updatedAt", progress.updatedAt());
        body.put("failedAlbums", progress.failedAlbums());
        return body;
    }
}
