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
| `TONGRLU_PLAYLIST_PROGRESS_DIR` | 歌单导入进度目录（**生产必设**，父目录需存在且进程可写） | 运行目录下的 `data/playlist_progress` |
| `TONGRLU_BATCH_PROGRESS_DIR` | 批处理进度目录（**生产必设**，父目录需存在且进程可写） | `data` |

生产推荐取值（父目录建一次即可，两级子目录不必预先创建）：

```bash
sudo mkdir -p /var/lib/tongrenlu/progress
sudo chown -R <tool服务用户> /var/lib/tongrenlu/progress
```

```
TONGRLU_PLAYLIST_PROGRESS_DIR=/var/lib/tongrenlu/progress/playlist
TONGRLU_BATCH_PROGRESS_DIR=/var/lib/tongrenlu/progress/batch
```

## 部署注意

1. **必须从 `f4569f3` 或其后继提交构建。** `origin/master`（`5197e7a`）仍是旧代码：断点续传写死了旧仓库绝对路径、Hutool 调用点没有超时、Basic Auth 带明文弱口令默认值且没有 fail-fast。在服务器上 clone/pull 之后直接构建，会把这些旧代码原样带进生产。要么先 push 再拉，要么 `git fetch && git checkout f4569f3`，要么直接用已构建好的 jar。部署前用 `git rev-parse HEAD` 确认拿到的确实是新提交。
2. **两个进度目录变量必须显式设置，且父目录必须存在、进程可写。** 写进度失败只会记一条 error 日志、不会让请求失败 —— 断点续传会静默失效，等于这次修复白做。
3. **启用认证时口令不能为空、也不能仍是默认值 `tongrenlu`**，否则应用**启动直接失败**（这是有意的 fail-fast）。验证要看**新进程**的启动日志（会出现 `IllegalStateException` 堆栈），别只看 curl 通不通：systemd 的 `EnvironmentFile` 路径写错、或重启的是另一个实例时，会出现「新实例起不来、旧实例还在扛」的假象。
4. **两个端点的 `done` 文案语义不同，脚本不要混用**：导入流是「进度文件已清除，导入流程结束」，补导是「补导结束：成功 X / 跳过 Y / 失败 Z，失败清单仍有 N 条，进度文件未清除」—— 补导不清进度文件，也不改写续传位置。
5. **要上传的 jar 先过一遍预检**：`git rev-parse HEAD` 只保证源码对，不保证重启的那个 jar 是对的 —— `target/` 里可能留着几个月前的旧包（`ls target/*.jar` 一把就能捞到），而且代码没变化时 `mvn -o package` **不会**刷新 fat jar（repackage 打了日志，产物时间戳仍是上一次构建的）。用 `scripts/jar_preflight.py` 检查待上传的包，见下节。

## 部署前 jar 预检（scripts/jar_preflight.py）

```bash
# 本地构建后：检查要传上去的那个 jar
python3 scripts/jar_preflight.py target/tongrenlu-tool.jar

# 上传到服务器后：再跑一次，确认传输没损坏、没拿错包
python3 scripts/jar_preflight.py /opt/tongrenlu/tongrenlu-tool.jar --expect-sha256 <本地产物的SHA-256>
```

只依赖 Python 标准库。检查项与不通过时的含义：

| 检查 | 不通过说明 |
| --- | --- |
| MANIFEST 有 `Main-Class` / `Start-Class` | 是薄包（`x.jar.original`），`java -jar` 直接起不来 |
| 打包进去的 `app.basic-auth.password` 默认值为空 | 仍是明文弱口令默认值 `tongrenlu`，旧代码被打包进来了 |
| 8 个 tool 修复相关类在 `BOOT-INF/classes` | 缺类 = 这批修复没打进去 |
| 3 个 dao 类在嵌套 `BOOT-INF/lib/tongrenlu-dao-*.jar` | 缺类 = dao 侧修复没打进去（注意 dao 类不在 `BOOT-INF/classes`，别误判成缺失） |
| `BOOT-INF/lib` 依赖数 > 0 | 为 0 = 不是 fat jar |
| SHA-256 与期望一致 | 传输损坏或拿错了文件（`.original` 与 fat jar 只差文件名后缀） |

退出码：`0` = PASS；`1` = FAIL（**不要部署**）；`2` = 用法错误。可直接当部署脚本的闸门。

> 本 jar 不含提交号（没有 build-info / git.properties），所以「包是不是新的」只能靠上面的内容检查判断，**不能靠文件时间戳**。

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
| `app.playlist-import.progress-dir` | 进度目录；对应环境变量 `TONGRLU_PLAYLIST_PROGRESS_DIR`、系统属性 `-Dtongrenlu.playlist.progress-dir` | 运行目录下的 `data/playlist_progress` |
| `app.playlist-import.retry-budget` | 入库类失败的自动重试预算 N | `3` |
| `app.playlist-import.permanent-ttl-days` | permanent 条目保留天数 | `30` |
| `app.playlist-import.retry-scan-interval-ms` | 自动补导扫描周期 | `1800000`（30 分钟） |
| `app.playlist-import.retry-scan-initial-delay-ms` | 扫描首次延迟 | `600000`（10 分钟） |
| `app.batch-import.progress-dir` | 批处理（`/album/import`、`/artist/import`）的进度目录，`album_progress.txt` / `progress.txt` / `artist_progress.txt` 都放这里；对应环境变量 `TONGRLU_BATCH_PROGRESS_DIR`、系统属性 `-Dtongrenlu.batch.progress-dir` | 运行目录下的 `data` |
| `app.batch-import.album-source` | 批处理的专辑源文件（`/album/import` 用）；仓库里的同名文件不移动、不删除 | `classpath:data/ls.txt` |

> 旧变量 `TONGRLU_PLAYLIST_PROGRESS_FILE`（旧的单文件变量）与拼错的 `TONGRENU_BATCH_PROGRESS_DIR` **不再识别**；
> 保留在环境里也不会生效。

## 自动补导扫描

tool 模块内置定时扫描（`@Scheduled`）：遍历进度目录里的歌单，只重试 `type=insert`
且 `attempts` 未达预算上限的条目；`permanent` / `transient` / 次数用尽的条目一律跳过并在日志里说明。
扫描与人工导入、人工补导共用同一把歌单锁。
