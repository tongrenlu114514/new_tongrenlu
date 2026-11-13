#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jar 级部署预检：确认要上传的 jar 确实是「当前代码 + 可执行 fat jar」。

背景（通路录 tongrenlu 实战踩坑）：
  1) git rev-parse HEAD 只能保证源码对，不保证重启的那个 jar 是对的 —— target/ 里
     可能躺着几个月前的旧包（ls target/*.jar 一把就能捞到）。
  2) mvn -o package 在代码没变化时**不会**刷新 fat jar（实测：repackage 日志打了，
     但 jar 的文件 mtime 与内部条目时间戳都停在上一次构建），所以「再打一次包」
     不等于「产物是新的」。
  3) dao 模块的类不在 BOOT-INF/classes 里，而是打进了嵌套的
     BOOT-INF/lib/tongrenlu-dao-*.jar —— 只查 BOOT-INF/classes 会误报 MISS。
  4) package 会同时产出两个包：tongrenlu-tool.jar（fat，可执行）与
     tongrenlu-tool.jar.original（薄包，无 Main-Class，java -jar 起不来）。
     后者不以 .jar 结尾，ls *.jar 看不见它 —— 通配符挑文件容易挑错。

用法：
  python3 tongrenlu-tool/scripts/jar_preflight.py                      # 用默认路径
  python3 tongrenlu-tool/scripts/jar_preflight.py <jar路径>              # 指定 jar
  python3 tongrenlu-tool/scripts/jar_preflight.py <jar路径> --expect-sha256 <64位hex>
      # 带期望指纹：比对上传前后是否传坏、是否拿错了 .original

默认 jar：<脚本所在模块>/target/tongrenlu-tool.jar
         （即 tongrenlu-tool/target/tongrenlu-tool.jar，与脚本位置无关，从哪跑都成立）

退出码：0 = 可部署；1 = 不可部署（有检查项不通过）；2 = 用法错误。
        可直接串进部署脚本做闸门：... || exit 1
"""

import hashlib
import io
import os
import sys
import zipfile
from datetime import datetime

# tool 模块自己的修复类（在 BOOT-INF/classes 下）
TOOL_CLASSES = {
    "PlaylistProgressStore": "BOOT-INF/classes/info/tongrenlu/support/PlaylistProgressStore.class",
    "PlaylistRetryJob": "BOOT-INF/classes/info/tongrenlu/PlaylistRetryJob.class",
    "PlaylistFailedController": "BOOT-INF/classes/info/tongrenlu/PlaylistFailedController.class",
    "PlaylistImportLock": "BOOT-INF/classes/info/tongrenlu/support/PlaylistImportLock.class",
    "FailedAlbumRetryService": "BOOT-INF/classes/info/tongrenlu/support/FailedAlbumRetryService.class",
    "AlbumImportRunner": "BOOT-INF/classes/info/tongrenlu/support/AlbumImportRunner.class",
    "FailedAlbumRetryScheduler": "BOOT-INF/classes/info/tongrenlu/support/FailedAlbumRetryScheduler.class",
    "BatchImportPaths": "BOOT-INF/classes/info/tongrenlu/support/BatchImportPaths.class",
}

# dao 模块的类（在嵌套 jar 里，不在 BOOT-INF/classes）
DAO_CLASSES = {
    "CloudMusicHttpClient": "info/tongrenlu/support/CloudMusicHttpClient.class",
    "CloudMusicAlbumFetchException": "info/tongrenlu/support/CloudMusicAlbumFetchException.class",
    "CloudMusicRequestException": "info/tongrenlu/support/CloudMusicRequestException.class",
}

# 打包进去的鉴权默认值：必须是空（fail-fast），不能是明文弱口令
FORBIDDEN_AUTH_DEFAULTS = ("tongrenlu",)

# 已废弃、不再识别的环境变量名：留在配置里不生效，但要提醒
LEGACY_ENV_NAMES = ("TONGRLU_PLAYLIST_PROGRESS_FILE", "TONGRENU_")

REBUILD_HINT = """  ▶ 怎么修：
    1) 源码要对：git rev-parse HEAD 必须等于 f4569f3 或其后继提交
       （origin/master=5197e7a 仍是旧代码：断点续传写死旧路径、无 Hutool 超时、
        Basic Auth 带明文弱口令默认值且没有 fail-fast）。
    2) 重新构建，并且独占 target/（别与别的 Maven 构建并发写同一个 target）：
         mvn -o -f <仓库>/pom.xml clean package
       注意：代码没变化时 `package` 不会刷新 fat jar（repackage 日志照打，但产物
       仍是上一次构建的），必须用 `clean package` 才会真正重写。
       ⚠️ 不要用 `-pl <模块>` 单模块跑 clean package：会因缺 -am 而 BUILD FAILURE，
       而且 clean 已经把产物删掉了。请在全仓库根目录执行上面的命令。
    3) 只上传 fat 包 tongrenlu-tool/target/tongrenlu-tool.jar，
       不要传 tongrenlu-tool.jar.original（薄包，java -jar 起不来）。
       挑产物用 `ls -la target/ | grep -i jar`，`ls *.jar` 看不到 .original。
    4) 传到服务器后再跑一次本脚本，可用 --expect-sha256 比对是否传坏。"""


def default_jar_path():
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(here, "..", "target", "tongrenlu-tool.jar"))


def die(msg, code=2):
    sys.stderr.write("错误：" + msg + "\n")
    sys.exit(code)


def main():
    args = sys.argv[1:]
    expect_sha = None
    positional = []
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--expect-sha256":
            if i + 1 >= len(args):
                die("--expect-sha256 后面缺少 64 位 hex")
            expect_sha = args[i + 1].strip().lower()
            i += 2
            continue
        if a.startswith("-"):
            die("未知参数：" + a)
        positional.append(a)
        i += 1

    if len(positional) > 1:
        die("参数过多，只接受一个 jar 路径：" + " ".join(positional))

    jar = positional[0] if positional else default_jar_path()
    if not os.path.isfile(jar):
        die("找不到文件：" + jar + "（默认路径：" + default_jar_path() + "）")

    size_mb = os.path.getsize(jar) / 1024 / 1024
    mtime = datetime.fromtimestamp(os.path.getmtime(jar)).strftime("%Y-%m-%d %H:%M:%S")
    sha = hashlib.sha256(open(jar, "rb").read()).hexdigest()

    print("=== 产物指纹（上传前后对比这一段即可确认没传坏、没拿错）===")
    print("  文件       : " + os.path.abspath(jar))
    print("  大小       : %.2f MiB" % size_mb)
    print("  mtime      : " + mtime)
    print("  SHA-256    : " + sha)
    if expect_sha:
        ok = (sha == expect_sha)
        print("  期望 SHA   : " + expect_sha)
        print("  结论       : " + ("一致（传输完整）" if ok else "★ 不一致（传坏了或拿错文件）"))

    fails = []
    warns = []
    if expect_sha and sha != expect_sha:
        fails.append("SHA-256 与期望不一致")

    z = zipfile.ZipFile(jar)
    names = set(z.namelist())

    print("\n=== MANIFEST（是否可执行 fat jar）===")
    mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
    has_main = False
    has_start = False
    for line in mf.splitlines():
        if line.startswith(("Main-Class", "Start-Class")):
            print("  " + line)
        if line.startswith("Main-Class"):
            has_main = True
        if line.startswith("Start-Class"):
            has_start = True
    if not (has_main and has_start):
        fails.append("MANIFEST 缺少 Main-Class/Start-Class —— 这是薄包"
                     "（如 tongrenlu-tool.jar.original），不可执行，不要上传")

    print("\n=== 打包进去的 application.properties（鉴权默认值）===")
    props = None
    props_entry = None
    for cand in ("BOOT-INF/classes/application.properties", "application.properties"):
        if cand in names:
            props = z.read(cand).decode("utf-8", "replace")
            props_entry = cand
            print("  来源条目：" + cand)
            break
    if props is None:
        fails.append("jar 内找不到 application.properties")
    else:
        auth_line = ""
        for line in props.splitlines():
            if "basic-auth.password" in line:
                auth_line = line.strip()
                print("  " + line.strip())
        if not auth_line:
            fails.append("打包的 properties 里没有 basic-auth.password")
        else:
            for bad in FORBIDDEN_AUTH_DEFAULTS:
                if bad in auth_line and ":}" in auth_line:
                    default = auth_line.split(":}", 1)[1]
                    if default.strip() == bad:
                        fails.append("打包进去的鉴权口令默认值仍是弱口令 '%s'" % bad)
        for key in ("app.playlist-import.retry-budget",
                    "app.playlist-import.permanent-ttl-days",
                    "app.playlist-import.retry-scan-interval-ms",
                    "app.playlist-import.retry-scan-initial-delay-ms"):
            for line in props.splitlines():
                if line.startswith(key + "="):
                    print("  " + line.strip())

    print("\n=== 已废弃的环境变量残留检查（残留不生效，但要提醒）===")
    legacy_hits = []
    for entry in sorted(n for n in names
                         if n.endswith(".properties") and n.count("/") <= 2):
        try:
            body = z.read(entry).decode("utf-8", "replace")
        except KeyError:
            continue
        for line in body.splitlines():
            for legacy in LEGACY_ENV_NAMES:
                if legacy in line:
                    legacy_hits.append((entry, legacy, line.strip()))
    if legacy_hits:
        for entry, legacy, line in legacy_hits:
            print("  WARN %s 里仍有已废弃变量 %s → %s" % (entry, legacy, line))
        warns.append("打包的 properties 里残留已废弃的环境变量名（%s），这些名字不再识别、留着不生效"
                     % "、".join(sorted({h[1] for h in legacy_hits})))
    else:
        print("  无残留（%s 都不在包里）" % "、".join(LEGACY_ENV_NAMES))

    print("\n=== tool 自己的类（BOOT-INF/classes）===")
    for label, path in sorted(TOOL_CLASSES.items()):
        ok = path in names
        print("  %s %s" % ("OK  " if ok else "MISS", label))
        if not ok:
            fails.append("缺少类 " + label)

    print("\n=== 嵌套 dao jar（不在 BOOT-INF/classes，别误判成缺失）===")
    dao_jars = sorted(n for n in names
                      if n.startswith("BOOT-INF/lib/tongrenlu-dao-") and n.endswith(".jar"))
    print("  数量：%d 个" % len(dao_jars))
    for n in dao_jars:
        print("    - " + n)
    if not dao_jars:
        fails.append("缺少嵌套 dao jar（BOOT-INF/lib/tongrenlu-dao-*.jar）")
        dao_class_names = set()
    else:
        dao_class_names = set()
        for n in dao_jars:
            inner = zipfile.ZipFile(io.BytesIO(z.read(n)))
            dao_class_names |= set(inner.namelist())
        for label, path in sorted(DAO_CLASSES.items()):
            ok = path in dao_class_names
            print("  %s %s" % ("OK  " if ok else "MISS", label))
            if not ok:
                fails.append("缺少类 " + label)

    deps = len([n for n in names if n.startswith("BOOT-INF/lib/")])
    print("\n=== BOOT-INF/lib 依赖数（fat jar 特征）===")
    print("  %d 个" % deps)
    if deps == 0:
        fails.append("BOOT-INF/lib 为空 —— 这不是 fat jar")

    print("\n=== 结论 ===")
    if fails:
        print("  能不能用：不能，缺 %d 项" % len(fails))
        print("  缺什么：")
        for f in fails:
            print("    - " + f)
        for w in warns:
            print("  WARN " + w)
        print("  ★ 不要部署这个包")
        print(REBUILD_HINT)
        return 1
    print("  能不能用：能部署。")
    print("  依据：可执行 fat jar（Main-Class/Start-Class 齐全）、鉴权默认口令为空、"
          "tool 修复类 %d 个与 dao 修复类 %d 个齐全、BOOT-INF/lib 依赖 %d 个。"
          % (len(TOOL_CLASSES), len(DAO_CLASSES), deps))
    for w in warns:
        print("  WARN " + w)
    print("  （本 jar 不含提交号，新旧只能靠上面的内容检查判断，所以务必跑本脚本。）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
