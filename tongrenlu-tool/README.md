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
5. **上传前对要部署的那个 jar 跑一遍预检**：`git rev-parse HEAD` 只保证源码对，不保证重启的那个 jar 是对的 —— `target/` 里可能躺着几个月前的旧包，而且代码没变化时 `mvn -o package` **不会**刷新 fat jar（repackage 打了日志，产物时间戳仍是上一次构建的）。两条路：有 `python3` 就跑 `python3 tongrenlu-tool/scripts/jar_preflight.py`（**不带参数即用默认路径** `tongrenlu-tool/target/tongrenlu-tool.jar`）；没有 `python3` 就用 `unzip` 三连（见下节）。**① `mvn -o package` 会同时产出 `tongrenlu-tool.jar`（fat，可执行）与 `tongrenlu-tool.jar.original`（薄包，不可执行），只上传前者**；`ls -la target/ | grep -i jar` 才看得到 `.original`，`ls *.jar` 看不到它。**② dao 的类在嵌套的 `BOOT-INF/lib/tongrenlu-dao-*.jar` 里，`unzip -l` 查不到不代表缺失。**
6. **要拿到新产物，必须 `clean package`**：实测在代码未变时重跑 `mvn -o package`，日志照打 `Replacing main artifact ... with repackaged archive`，但产物的 mtime 与 jar 内条目时间戳都停在上一次构建，SHA-256 也一模一样 —— 也就是**包根本没被重写**。只有 `clean package` 会真正刷新（实测 SHA-256 从 `9c87a6ed…` 变为 `7e880801…`，条目时间戳全部更新）。⚠️ 用 `-pl <模块>` 单模块跑 `clean package` 会因缺 `-am` 而 BUILD FAILURE，且 `clean` 已经把产物删掉了 —— 请在全仓库根目录跑 `mvn -o clean package`。另外打包时要确认没有别的 Maven 构建在跑（两个构建并发写同一个 `target/` 会互相干扰）。

## 部署前自检：jar 预检（tongrenlu-tool/scripts/jar_preflight.py）

有 `python3` 时（在仓库根目录执行；**不带参数就用默认路径** `tongrenlu-tool/target/tongrenlu-tool.jar`）：

```bash
# 本地构建后：检查要传上去的那个 jar（不带参数即默认路径）
python3 tongrenlu-tool/scripts/jar_preflight.py

# 或显式指定
python3 tongrenlu-tool/scripts/jar_preflight.py tongrenlu-tool/target/tongrenlu-tool.jar

# 上传到服务器后：再跑一次，确认传输没损坏、没拿错包
python3 tongrenlu-tool/scripts/jar_preflight.py /opt/tongrenlu/tongrenlu-tool.jar --expect-sha256 <脚本打印的SHA-256>
```

> SHA-256 用脚本自己打印的那个值，**不要在部署文档里写死** —— 每次重新构建产物都会变。

> 参数顺序不限（`--expect-sha256` 后面必须紧跟 64 位 hex，大小写均可）；未知参数、缺参数、给了多个 jar 路径、hex 长度不对或含非十六进制字符，都直接以退出码 `2` 报错，不会误判成"可部署"。区分「用法写错了」与「包本身有问题」很重要：前者提示你改命令，后者提示你换产物，两者的排查方向完全不同。

没有 `python3` 时用 `unzip` 三连，同样能判出是不是 fat jar、是不是新代码：

```bash
unzip -p tongrenlu-tool.jar META-INF/MANIFEST.MF | grep -E "Main-Class|Start-Class"
unzip -p tongrenlu-tool.jar BOOT-INF/classes/application.properties | grep basic-auth.password
unzip -l tongrenlu-tool.jar | grep -E "PlaylistProgressStore|PlaylistRetryJob|PlaylistFailedController"
```

> 判定标准：MANIFEST 必须有 `Main-Class` 与 `Start-Class`（薄包 `.jar.original` 没有，`java -jar` 起不来）；`basic-auth.password` 的默认值必须为空（`${TOOL_AUTH_PASSWORD:}`，不能是弱口令默认值）；三个 tool 类必须出现（dao 的类在嵌套 `BOOT-INF/lib/tongrenlu-dao-*.jar` 里，`unzip -l` 查不到不代表缺失）。

脚本只依赖 Python 标准库，检查项与不通过时的含义：

| 检查 | 不通过说明 |
| --- | --- |
| MANIFEST 有 `Main-Class` / `Start-Class` | 是薄包（`x.jar.original`），`java -jar` 直接起不来 |
| 打包进去的 `app.basic-auth.password` 默认值为空 | 仍是明文弱口令默认值 `tongrenlu`，旧代码被打包进来了 |
| 8 个 tool 修复相关类在 `BOOT-INF/classes` | 缺类 = 这批修复没打进去 |
| 嵌套 dao jar 的**数量**与其中 3 个 dao 类 | dao 类不在 `BOOT-INF/classes`，查不到不代表缺失；嵌套包按 `tongrenlu-dao-*.jar` 前缀匹配，不写死版本号 |
| `BOOT-INF/lib` 依赖数 > 0 | 为 0 = 不是 fat jar |
| SHA-256 与期望一致 | 传输损坏或拿错了文件（`.original` 与 fat jar 只差文件名后缀） |
| 已废弃环境变量 `TONGRLU_PLAYLIST_PROGRESS_FILE` / `TONGRENU_*` 是否残留 | **警告级**：这些名字不再识别、留着不生效，但说明配置没清理干净 |

示例输出（fat 包，通过）：

```
=== 结论 ===
  能不能用：能部署。
  依据：可执行 fat jar（Main-Class/Start-Class 齐全）、鉴权默认口令为空、tool 修复类 8 个与 dao 修复类 3 个齐全、BOOT-INF/lib 依赖 88 个。
```

示例输出（薄包 `.jar.original`，不通过，退出码 1）：

```
=== 结论 ===
  能不能用：不能，缺 11 项
  缺什么：
    - MANIFEST 缺少 Main-Class/Start-Class —— 这是薄包（如 tongrenlu-tool.jar.original），不可执行，不要上传
    - 缺少类 PlaylistRetryJob
    ...
    - BOOT-INF/lib 为空 —— 这不是 fat jar
  ★ 不要部署这个包
  ▶ 怎么修：
    1) 源码要对：git rev-parse HEAD 必须等于 f4569f3 或其后继提交 ...
    2) 重新构建，并且独占 target/（别与别的 Maven 构建并发写同一个 target） ...
    3) 只上传 fat 包 tongrenlu-tool/target/tongrenlu-tool.jar，不要传 tongrenlu-tool.jar.original ...
```

go/no-go 判定：脚本最后输出**「能不能用 + 缺什么 + 缺的话该怎么重新 build」**三段。
退出码：`0` = PASS（可部署）；`1` = FAIL（**不要部署**）；`2` = 用法错误。可直接当部署脚本的闸门。

> 本 jar 不含提交号（没有 build-info / git.properties），所以「包是不是新的」只能靠上面的内容检查判断，**不能靠文件时间戳**。

### 预检脚本自己的回归自测（tongrenlu-tool/scripts/test_jar_preflight.py）

预检脚本是部署闸门，闸门本身也得有人守。本自测把各场景固化成可重复执行的用例：

```bash
python3 tongrenlu-tool/scripts/test_jar_preflight.py          # 跑全部用例
python3 tongrenlu-tool/scripts/test_jar_preflight.py -v       # 详细输出
# 当闸门用：python3 tongrenlu-tool/scripts/test_jar_preflight.py || exit 1
```

- 只用 Python 标准库；**所有探针 jar 都在临时目录里现造**，不依赖本机 `target/` 产物，因此在没有构建过的机器上也能跑。
- 用子进程调用被测脚本，断言的是**真实退出码**（`0` 可部署 / `1` 不可部署 / `2` 用法错），而不是函数返回值 —— 闸门真正依赖的就是退出码。
- 覆盖场景：fat jar（显式路径 / 默认路径且与 cwd 无关）、薄包 `.jar.original`、指纹不符、63 位 hex、含非 hex 字符、未知参数、多个路径、文件不存在、大写 hex（Windows `Get-FileHash` 输出是大写）、选项前置、`--expect-sha256` 缺值、弱口令默认值、硬编码弱口令、硬编码强口令（且不得回显）、占位符无默认值、缺 tool 类、缺嵌套 dao jar、缺 `application.properties`、废弃变量残留（警告级、不拦部署）、失败时必须给出可执行的重建指引、检查表类清单不得被改小。
- 类名、弱口令默认值、废弃变量名一律**从被测脚本 import**（单一事实来源），不在自测里硬编码第二份；自测只守「覆盖面下限」两个数字，防止有人悄悄把检查项删少。

**退出码契约（对外契约：改脚本行为必须同步改这张表，自测会逐条校验）**

| 编号 | 场景 | 期望退出码 | 含义 |
| --- | --- | --- | --- |
| S1 | 不带参数，用默认路径（按脚本位置解析，与 cwd 无关） | 0 | 可部署 |
| S2 | 显式指定合格的 fat jar | 0 | 可部署 |
| S3 | 指定薄包（无 Main-Class） | 1 | 不可部署 |
| S4 | 指纹格式合法但值不符 | 1 | 不可部署 |
| S5 | hex 指纹位数不足 64 | 2 | 用法错 |
| S6 | hex 指纹含非十六进制字符 | 2 | 用法错 |
| S7 | 未知参数 | 2 | 用法错 |
| S8 | 多余的 jar 路径 | 2 | 用法错 |
| S9 | 文件不存在 | 2 | 用法错 |
| S10 | `--expect-sha256` 缺值 | 2 | 用法错 |
| S11 | 大写 hex（Windows `Get-FileHash` 的输出形态） | 0 | 可部署 |
| S12 | 含废弃变量名的包（只告警，不阻断） | 0 | 可部署 |

`0` = 可部署；`1` = 包有问题（换产物 / 重新 build）；`2` = 用法错（改命令）。后两者排查方向
完全不同 —— 用法错让你去改参数，包有问题让你去重新 build，混在一起容易白折腾一轮。

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
