package info.tongrenlu;

import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.support.PlaylistProgressStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.codec.ServerSentEvent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌单导入任务的断点续传行为验证：
 * 进度文件歌单匹配 → 从断点继续；歌单不匹配 → 从头开始。
 */
class PlaylistImportJobResumeTest {

    private static final Long PLAYLIST_ID = 100L;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("进度文件歌单匹配时从断点继续导入，不再重复处理前面的专辑")
    void resumesFromSavedIndex() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));
        store.saveIndex(PLAYLIST_ID, 1);

        PlaylistImportJob job = new PlaylistImportJob(musicService, articleService, store);
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("从第 2 个专辑继续导入"));
        verify(musicService, never()).saveCloudMusicAlbum(11L);
        verify(musicService).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
    }

    @Test
    @DisplayName("进度序号越界时归零从头开始，不抛 IndexOutOfBoundsException")
    void outOfRangeIndexStartsFromBeginning() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));
        // 当前歌单只有 3 个专辑，进度序号 99 已越界
        store.saveIndex(PLAYLIST_ID, 99);

        PlaylistImportJob job = new PlaylistImportJob(musicService, articleService, store);
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        verify(musicService).saveCloudMusicAlbum(11L);
        verify(musicService).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
    }

    @Test
    @DisplayName("进度文件属于其它歌单时从头开始，不静默漏导前面的专辑")
    void mismatchedPlaylistIdStartsFromBeginning() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));
        store.saveIndex(999L, 2);

        PlaylistImportJob job = new PlaylistImportJob(musicService, articleService, store);
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("继续导入"));
        verify(musicService).saveCloudMusicAlbum(11L);
        verify(musicService).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
    }
}
