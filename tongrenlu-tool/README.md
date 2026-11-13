# tongrenlu-tool 管理接口

网易云歌单专辑的导入、补导与失败清单管理。**本模块全部接口都需要 HTTP Basic 认证**
（`TOOL_AUTH_ENABLED` / `TOOL_AUTH_USERNAME` / `TOOL_AUTH_PASSWORD`，启用认证且口令为空或仍为
默认值 `tongrenlu` 时启动直接失败）。

线上地址：`http://121.37.183.44:8080`（web 模块在 8443，context-path `/tongrenlu`）。

## 环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `TOOL_AUTH_ENABLED` | 是否启用 Basic 认证 | `true` |
| `TOOL_AUTH_USERNAME` | 认证账号 | `admin` |
| `TOOL_AUTH_PASSWORD` | 认证口令（必填，不能是默认值） | 空 |
| `TONGRLU_PLAYLIST_PROGRESS_DIR` | 歌单导入进度目录（必须可写，父目录需存在） | 运行目录下的 `data/playlist_progress` |
| `TONGRLU_BATCH_PROGRESS_DIR` | 批处理进度目录 | `data` |

## 导入歌单专辑（SSE）

```bash
curl -u admin:'<口令>' -N "http://127.0.0.1:8080/playlist/import?playlistId=123"
```

按歌单顺序逐个导入专辑，进度写入 `playlist_<id>.json`，中断后再次调用会从断点续传。
事件名：`start` / `info` / `complete` / `success` / `skip` / `failed` / `error` / `fatal` / `warn` / `stats` / `done`。

## 查看失败清单（只读 JSON）

```bash
curl -u admin:'<口令>' "http://127.0.0.1:8080/playlist/failed?playlistId=123"
```

返回：

```json
{
  "playlistId": 123,
  "index": 41,
  "updatedAt": "2026-11-14T00:40:00Z",
  "failedAlbums": [
    {"albumId": 555, "type": "insert", "reason": "数据库写入失败", "at": "2026-11-14T00:40:00Z", "attempts": 1}
  ]
}
```

失败类型：

| type | 含义 | 谁来重试 |
| --- | --- | --- |
| `insert` | 入库等异常，专辑在上游可能是好的 | 人工补导 + 定时扫描（预算 N 次） |
| `transient` | 上游超时 / IO / 解析失败，结果未知 | 导入流自己（水位不越过它，下一轮自动重试） |
| `permanent` | 上游明确没有这个专辑（code!=200） | 不重试；超过保留期自动从清单移除 |

## 按失败清单补导（SSE）

```bash
# 自动筛清单里 type=insert 的条目补导
curl -u admin:'<口令>' -N -X POST "http://127.0.0.1:8080/playlist/retry-failed?playlistId=123"

# 指定失败类型（人工可以强制重试 transient / permanent）
curl -u admin:'<口令>' -N -X POST "http://127.0.0.1:8080/playlist/retry-failed?playlistId=123&type=transient"

# 指定专辑 ID（人工明确指定时以这个列表为准，不再受 type 限制）
curl -u admin:'<口令>' -N -X POST "http://127.0.0.1:8080/playlist/retry-failed?playlistId=123&albumIds=555,666"
```

语义要点：

- **只销账、不动水位**：被补导的专辑水位早已越过，补导绝不改写续传位置（`index`）。
- **不清进度文件**：清单里可能还有别的条目，删掉进度文件会丢续传位置。
- **失败重新分类**：补导时若变成上游 404，会记为 `permanent` 而不是继续当 `insert` 补下去。
- **失败计数**：人工手动补导会把 `attempts` 归零，重新给一份完整预算；自动扫描则继续累加。
- **同歌单互斥**：与导入流、定时扫描共用同一把锁，并发时后到的一方会收到 `warn` 事件并跳过。
- `done` 事件的文案是真实结果（`补导结束：成功 X / 跳过 Y / 失败 Z，失败清单仍有 N 条，进度文件未清除`），
  与导入流的 `进度文件已清除，导入流程结束` 含义不同，不要混用脚本。

## 配置项

| 配置 | 说明 | 默认 |
| --- | --- | --- |
| `app.playlist-import.progress-dir` | 进度目录 | 运行目录下的 `data/playlist_progress` |
| `app.playlist-import.retry-budget` | 入库类失败的自动重试预算 N | `3` |
| `app.playlist-import.permanent-ttl-days` | permanent 条目保留天数 | `30` |
| `app.playlist-import.retry-scan-interval-ms` | 自动补导扫描周期 | `1800000`（30 分钟） |
| `app.playlist-import.retry-scan-initial-delay-ms` | 扫描首次延迟 | `600000`（10 分钟） |

## 自动补导扫描

tool 模块内置定时扫描（`@Scheduled`）：遍历进度目录里的歌单，只重试 `type=insert`
且 `attempts` 未达预算上限的条目；`permanent` / `transient` / 次数用尽的条目一律跳过并在日志里说明。
扫描与人工导入、人工补导共用同一把歌单锁。
