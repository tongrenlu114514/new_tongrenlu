#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jar 级部署预检：确认要上传的 jar 确实是「当前代码 + 可执行 fat jar」。

背景（通路录 tongrenlu 实战踩坑）：
  1) git rev-parse HEAD 只能保证源码对，不能保证重启的那个 jar 是对的 —— target/ 里
     可能躺着几个月前的旧包，ls target/*.jar 一把就能捞到。
  2) mvn -o package 在代码没变化时**不会**刷新 fat jar（实测：repackage 日志打了，
     但 jar 的文件 mtime 与内部条目时间戳都停在上一次构建），所以「再打一次包」
     不等于「产物是新的」。
  3) dao 模块的类不在 BOOT-INF/classes 里，而是打进了嵌套的
     BOOT-INF/lib/tongrenlu-dao-*.jar —— 只查 BOOT-INF/classes 会误报 MISS。

用法：
  python3 jar_preflight.py /path/to/tongrenlu-tool.jar
  python3 jar_preflight.py tongrenlu-tool.jar --expect-sha256 <64位hex>

退出码：0 = PASS；1 = FAIL（有检查项不通过）。可直接用于部署脚本做闸门。
"""

import hashlib
import io
import os
import sys
import zipfile
from datetime import datetime

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

DAO_CLASSES = {
    "CloudMusicHttpClient": "info/tongrenlu/support/CloudMusicHttpClient.class",
    "CloudMusicAlbumFetchException": "info/tongrenlu/support/CloudMusicAlbumFetchException.class",
    "CloudMusicRequestException": "info/tongrenlu/support/CloudMusicRequestException.class",
}

# 打包进去的鉴权默认值：必须是空（fail-fast），不能是明文弱口令
FORBIDDEN_AUTH_DEFAULTS = ("tongrenlu",)


def die(msg, code=2):
    sys.stderr.write("错误：" + msg + "\n")
    sys.exit(code)


def main():
    args = sys.argv[1:]
    if not args:
        die("用法：python3 jar_preflight.py <jar路径> [--expect-sha256 <hex>]")
    jar = args[0]
    expect_sha = None
    if "--expect-sha256" in args:
        try:
            expect_sha = args[args.index("--expect-sha256") + 1].strip().lower()
        except IndexError:
            die("--expect-sha256 后面缺少 64 位 hex")

    if not os.path.isfile(jar):
        die("找不到文件：" + jar)

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
        fails.append("MANIFEST 缺少 Main-Class/Start-Class —— 这是薄包，java -jar 起不来")

    print("\n=== 打包进去的 application.properties（鉴权默认值）===")
    props = None
    for cand in ("BOOT-INF/classes/application.properties", "application.properties"):
        if cand in names:
            props = z.read(cand).decode("utf-8", "replace")
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

    print("\n=== tool 自己的类（BOOT-INF/classes）===")
    for label, path in sorted(TOOL_CLASSES.items()):
        ok = path in names
        print("  %s %s" % ("OK  " if ok else "MISS", label))
        if not ok:
            fails.append("缺少类 " + label)

    print("\n=== 嵌套 dao jar 里的类（不在 BOOT-INF/classes，别误判）===")
    dao_jar = None
    for n in names:
        if n.startswith("BOOT-INF/lib/tongrenlu-dao-") and n.endswith(".jar"):
            dao_jar = n
            break
    if dao_jar is None:
        print("  MISS 嵌套 dao jar")
        fails.append("缺少嵌套 dao jar")
    else:
        print("  嵌套包：" + dao_jar)
        inner = zipfile.ZipFile(io.BytesIO(z.read(dao_jar)))
        inames = set(inner.namelist())
        for label, path in sorted(DAO_CLASSES.items()):
            ok = path in inames
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
        print("  FAIL（%d 项不通过）：" % len(fails))
        for f in fails:
            print("    - " + f)
        print("  ★ 不要部署这个包")
        return 1
    print("  PASS：可执行 fat jar、鉴权默认口令为空、修复相关类齐全、依赖完整。")
    print("  （本 jar 不含提交号，新旧只能靠上面的内容检查判断，所以务必跑本脚本。）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
