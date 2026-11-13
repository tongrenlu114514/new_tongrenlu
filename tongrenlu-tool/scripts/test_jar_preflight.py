#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jar_preflight.py 的回归自测。

用途：预检脚本是部署闸门，它自己必须有人守。本脚本把 Chief 复测过的 12 个场景
固化成可重复执行的用例，另外补了 5 个容易回归的边界场景。

设计约束（刻意为之）：
  · 只用标准库（unittest / tempfile / zipfile / io / subprocess / hashlib …）；
  · 全部探针 jar 都在临时目录里现造，**不依赖本机 target 产物**，
    因此在 CI、在别的机器、在没有构建过的仓库里都能跑；
  · 用子进程调用被测脚本，断言的是**真实退出码**（0 可部署 / 1 不可部署 / 2 用法错），
    而不是函数返回值 —— 闸门真正依赖的就是退出码。

用法：
  python3 tongrenlu-tool/scripts/test_jar_preflight.py            # 跑全部用例
  python3 tongrenlu-tool/scripts/test_jar_preflight.py -v         # 详细输出
  # 作为闸门：python3 tongrenlu-tool/scripts/test_jar_preflight.py || exit 1
"""

import hashlib
import io
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PREFLIGHT = os.path.join(SCRIPT_DIR, "jar_preflight.py")

# 类名、弱口令默认值、废弃变量名一律**从被测脚本 import**（单一事实来源，避免两边漂移）：
# 被测脚本新增检查项时，本文件自动跟着覆盖；被测脚本删掉检查项时，下面的覆盖面下限会拦下。
# 这里只留两个数字当覆盖面下限，不复制第二份类名清单。
MIN_TOOL_CLASSES = 8
MIN_DAO_CLASSES = 3


def load_under_test():
    import importlib.util
    spec = importlib.util.spec_from_file_location("jar_preflight_under_test", PREFLIGHT)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)      # 模块自带 __main__ 守卫，import 不会触发 main()
    return mod


MOD = load_under_test()
TOOL_CLASS_PATHS = list(MOD.TOOL_CLASSES.values())
DAO_CLASS_PATHS = list(MOD.DAO_CLASSES.values())
FORBIDDEN_AUTH_DEFAULTS = tuple(MOD.FORBIDDEN_AUTH_DEFAULTS)
LEGACY_ENV_NAMES = tuple(MOD.LEGACY_ENV_NAMES)

# 退出码契约：编号 → (场景, 期望退出码)。与 README 的「退出码契约」表逐条对应，
# 改脚本的行为必须同步改这张表和 README，否则 test_24 会失败。
CONTRACT = [
    ("S1", "不带参数，用默认路径（按脚本位置解析，与 cwd 无关）", 0),
    ("S2", "显式指定合格的 fat jar", 0),
    ("S3", "指定薄包（无 Main-Class）", 1),
    ("S4", "指纹格式合法但值不符", 1),
    ("S5", "hex 指纹位数不足 64", 2),
    ("S6", "hex 指纹含非十六进制字符", 2),
    ("S7", "未知参数", 2),
    ("S8", "多余的 jar 路径", 2),
    ("S9", "文件不存在", 2),
    ("S10", "--expect-sha256 缺值", 2),
    ("S11", "大写 hex（Windows Get-FileHash 的输出形态）", 0),
    ("S12", "含废弃变量名的包（只告警，不阻断）", 0),
]

EMPTY_CLASS = b"\xca\xfe\xba\xbe"


def build_fat_jar(path, auth_default="", auth_raw=None, legacy_names=(), drop_tool_classes=(),
                  drop_dao_jar=False, drop_dao_classes=(), drop_props=False,
                  with_manifest=True, dep_count=3, with_config_keys=True):
    """造一个「正常 fat jar」探针，可按需打各种洞。

    auth_raw：直接给出整行 basic-auth.password（用于测硬编码写法）；不给则用占位符写法。
    """
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        if with_manifest:
            z.writestr("META-INF/MANIFEST.MF",
                       "Manifest-Version: 1.0\n"
                       "Main-Class: org.springframework.boot.loader.launch.JarLauncher\n"
                       "Start-Class: info.tongrenlu.TongrenluToolApplication\n\n")
        props = [
            "app.basic-auth.enabled=${TOOL_AUTH_ENABLED:true}",
            "app.basic-auth.username=${TOOL_AUTH_USERNAME:admin}",
            "app.basic-auth.realm-name=tongrenlu-tool",
        ]
        if auth_raw is not None:
            props.append(auth_raw)
        else:
            props.append("app.basic-auth.password=${TOOL_AUTH_PASSWORD:%s}" % auth_default)
        if with_config_keys:
            props += [
                "app.playlist-import.retry-budget=3",
                "app.playlist-import.permanent-ttl-days=30",
                "app.playlist-import.retry-scan-interval-ms=1800000",
                "app.playlist-import.retry-scan-initial-delay-ms=600000",
            ]
        for name in legacy_names:
            props.append("# 已废弃变量残留：%s=/var/lib/tongrenlu/legacy" % name)
        if not drop_props:
            z.writestr("BOOT-INF/classes/application.properties", "\n".join(props) + "\n")
        for p in TOOL_CLASS_PATHS:
            if p not in drop_tool_classes:
                z.writestr(p, EMPTY_CLASS)
        if not drop_dao_jar:
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as inner:
                for p in DAO_CLASS_PATHS:
                    if p not in drop_dao_classes:
                        inner.writestr(p, EMPTY_CLASS)
            z.writestr("BOOT-INF/lib/tongrenlu-dao-1.0-SNAPSHOT.jar", buf.getvalue())
        for i in range(dep_count):
            z.writestr("BOOT-INF/lib/some-dep-%d.jar" % i, b"PK\x05\x06" + b"\x00" * 18)


def build_thin_jar(path):
    """造一个「薄包」探针：等价于 .jar.original —— 类在根目录、无 Main-Class、无嵌套依赖。"""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n\n")
        z.writestr("info/tongrenlu/support/PlaylistProgressStore.class", EMPTY_CLASS)
        z.writestr("application.properties",
                   "app.basic-auth.password=${TOOL_AUTH_PASSWORD:tongrenlu}\n")


def sha256_of(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def run_preflight(args, cwd=None, script=PREFLIGHT):
    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"       # Windows 控制台默认不是 UTF-8，中文断言会炸
    env["PYTHONUTF8"] = "1"
    proc = subprocess.run([sys.executable, script] + list(args),
                          capture_output=True, encoding="utf-8", errors="replace",
                          cwd=cwd, env=env)
    return proc.returncode, proc.stdout, proc.stderr


class JarPreflightTest(unittest.TestCase):

    def assert_code(self, expected, actual, out, sid=""):
        """断言**真实退出码**；失败时明确打印「期望 vs 实际」并附上被测脚本的原始输出。"""
        if actual != expected:
            raise AssertionError("%s 期望退出码 %d，实际 %d\n---- 被测脚本输出 ----\n%s"
                                 % (sid, expected, actual, out))

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp(prefix="jar_preflight_test_")
        cls.fat = os.path.join(cls.tmp, "tongrenlu-tool.jar")
        cls.thin = os.path.join(cls.tmp, "tongrenlu-tool.jar.original")
        cls.weak = os.path.join(cls.tmp, "weak-password.jar")
        cls.missing_class = os.path.join(cls.tmp, "missing-class.jar")
        cls.missing_dao = os.path.join(cls.tmp, "missing-dao.jar")
        cls.no_props = os.path.join(cls.tmp, "no-props.jar")
        cls.legacy = os.path.join(cls.tmp, "legacy-vars.jar")
        cls.hardcoded_weak = os.path.join(cls.tmp, "hardcoded-weak.jar")
        cls.hardcoded_strong = os.path.join(cls.tmp, "hardcoded-strong.jar")
        cls.no_default = os.path.join(cls.tmp, "no-default.jar")
        build_fat_jar(cls.fat)
        build_thin_jar(cls.thin)
        build_fat_jar(cls.weak, auth_default=FORBIDDEN_AUTH_DEFAULTS[0])
        build_fat_jar(cls.missing_class, drop_tool_classes=[TOOL_CLASS_PATHS[0]])
        build_fat_jar(cls.missing_dao, drop_dao_jar=True)
        build_fat_jar(cls.no_props, drop_props=True)
        build_fat_jar(cls.legacy, legacy_names=LEGACY_ENV_NAMES)
        build_fat_jar(cls.hardcoded_weak,
                      auth_raw="app.basic-auth.password=" + FORBIDDEN_AUTH_DEFAULTS[0])
        build_fat_jar(cls.hardcoded_strong, auth_raw="app.basic-auth.password=S3cret-Not-Weak")
        build_fat_jar(cls.no_default, auth_raw="app.basic-auth.password=${TOOL_AUTH_PASSWORD}")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    # ── 场景 1-2：正常 fat jar ────────────────────────────────────────────────
    def test_01_fat_jar_explicit_path_passes(self):
        code, out, _ = run_preflight([self.fat])
        self.assert_code(0, code, out)
        self.assertIn("能部署", out)

    def test_02_fat_jar_default_path_passes(self):
        """默认路径按脚本位置解析，且与 cwd 无关（不能依赖本机 target 产物）。"""
        layout = os.path.join(self.tmp, "fake_repo")
        scripts = os.path.join(layout, "tool", "scripts")
        target = os.path.join(layout, "tool", "target")
        os.makedirs(scripts)
        os.makedirs(target)
        shutil.copyfile(PREFLIGHT, os.path.join(scripts, "jar_preflight.py"))
        build_fat_jar(os.path.join(target, "tongrenlu-tool.jar"))
        script = os.path.join(scripts, "jar_preflight.py")

        code, out, _ = run_preflight([], cwd=layout, script=script)
        self.assert_code(0, code, out)

        # cwd 在别处也必须命中同一个默认路径
        code, out, _ = run_preflight([], cwd=self.tmp, script=script)
        self.assert_code(0, code, out)

    # ── 场景 3：薄包 ────────────────────────────────────────────────────────
    def test_03_thin_jar_fails(self):
        code, out, _ = run_preflight([self.thin])
        self.assert_code(1, code, out)
        self.assertIn("薄包", out)
        self.assertIn("不要部署这个包", out)

    def test_04_failure_output_contains_rebuild_guidance(self):
        """失败时必须给出可执行的重建指引，否则闸门只是拦人而不救人。"""
        code, out, _ = run_preflight([self.thin])
        self.assert_code(1, code, out)
        self.assertIn("怎么修", out)
        self.assertIn("clean package", out)
        self.assertIn(".jar.original", out)

    # ── 场景 4：格式合法但指纹不符 ───────────────────────────────────────────
    def test_05_sha_mismatch_fails(self):
        code, out, _ = run_preflight([self.fat, "--expect-sha256", "0" * 64])
        self.assert_code(1, code, out)
        self.assertIn("不一致", out)

    # ── 场景 5-6：指纹格式错 → 用法错（退出码 2），不能混进「包坏了」的 1 ─────
    def test_06_sha_too_short_is_usage_error(self):
        code, out, err = run_preflight([self.fat, "--expect-sha256", "a" * 63])
        self.assert_code(2, code, out + err)
        self.assertIn("63", err)

    def test_07_sha_non_hex_is_usage_error(self):
        code, out, err = run_preflight([self.fat, "--expect-sha256", "z" * 64])
        self.assert_code(2, code, out + err)

    # ── 场景 7-9：参数用法错 ───────────────────────────────────────────────
    def test_08_unknown_argument_is_usage_error(self):
        code, out, err = run_preflight(["--wat", self.fat])
        self.assert_code(2, code, out + err)

    def test_09_two_paths_is_usage_error(self):
        code, out, err = run_preflight([self.fat, self.thin])
        self.assert_code(2, code, out + err)

    def test_10_missing_file_is_usage_error(self):
        code, out, err = run_preflight([os.path.join(self.tmp, "nope.jar")])
        self.assert_code(2, code, out + err)
        self.assertIn("找不到文件", err)

    # ── 场景 10-11：大写 hex（Windows Get-FileHash 输出是大写，必须能用）──────
    def test_11_uppercase_sha_matches(self):
        sha = sha256_of(self.fat)
        code, out, _ = run_preflight([self.fat, "--expect-sha256", sha.upper()])
        self.assert_code(0, code, out)
        self.assertIn("一致", out)

    def test_12_uppercase_sha_with_option_before_path(self):
        """选项在路径之前 + 大写 hex：参数必须按 token 顺序消费，不能靠值猜位置。"""
        sha = sha256_of(self.fat)
        code, out, _ = run_preflight(["--expect-sha256", sha.upper(), self.fat])
        self.assert_code(0, code, out)

    # ── 场景 12：--expect-sha256 缺值 ──────────────────────────────────────
    def test_13_expect_sha_missing_value_is_usage_error(self):
        code, out, err = run_preflight([self.fat, "--expect-sha256"])
        self.assert_code(2, code, out + err)

    # ── 附加边界场景（Chief 的 12 条之外） ─────────────────────────────────
    def test_14_weak_default_password_fails(self):
        """打包进去的鉴权口令默认值仍是弱口令 → 必须拦住。"""
        code, out, _ = run_preflight([self.weak])
        self.assert_code(1, code, out)
        self.assertIn("弱口令", out)

    def test_15_missing_tool_class_fails(self):
        code, out, _ = run_preflight([self.missing_class])
        self.assert_code(1, code, out)
        self.assertIn("缺少类", out)

    def test_16_missing_nested_dao_jar_fails(self):
        code, out, _ = run_preflight([self.missing_dao])
        self.assert_code(1, code, out)
        self.assertIn("缺少嵌套 dao jar", out)

    def test_17_missing_properties_fails(self):
        code, out, _ = run_preflight([self.no_props])
        self.assert_code(1, code, out)
        self.assertIn("application.properties", out)

    def test_18_legacy_env_vars_warn_but_do_not_block(self):
        """废弃变量残留按警告级处理：出 WARN，退出码仍是 0（留着不生效，不该拦住部署）。"""
        code, out, _ = run_preflight([self.legacy])
        self.assert_code(0, code, out)
        self.assertIn("WARN", out)
        for name in LEGACY_ENV_NAMES:
            self.assertIn(name, out, "废弃变量没被点名：" + name)

    # ── 检查表契约：防止有人悄悄改小覆盖面 ────────────────────────────────
    def test_19_checklist_contract(self):
        """覆盖面下限：tool 类不得少于 8 个、dao 类不得少于 3 个（对应第1~8轮全部修复类）。

        类名本身从被测脚本 import（单一事实来源，不在本文件硬编码第二份），所以这里只守
        「数量下限」与「探针 jar 里确实装的就是被测脚本当前要检查的那些类」。
        """
        self.assertGreaterEqual(len(TOOL_CLASS_PATHS), MIN_TOOL_CLASSES,
                                "tool 检查项少于 %d 个，覆盖面被改小了" % MIN_TOOL_CLASSES)
        self.assertGreaterEqual(len(DAO_CLASS_PATHS), MIN_DAO_CLASSES,
                                "dao 检查项少于 %d 个，覆盖面被改小了" % MIN_DAO_CLASSES)
        with zipfile.ZipFile(self.fat) as z:
            names = set(z.namelist())
        for p in TOOL_CLASS_PATHS:
            self.assertIn(p, names, "探针 jar 里缺少被测脚本要检查的 tool 类：" + p)
        for p in DAO_CLASS_PATHS:
            self.assertTrue(any(n.endswith(".jar") for n in names), "探针 jar 缺嵌套 dao 包")
        self.assertTrue(FORBIDDEN_AUTH_DEFAULTS, "被测脚本的弱口令清单为空")
        self.assertTrue(LEGACY_ENV_NAMES, "被测脚本的废弃变量清单为空")

    # ── 弱口令检测（修死代码后补的回归） ─────────────────────────────────
    def test_20_hardcoded_weak_password_fails(self):
        """把弱口令直接写死在配置里（不走占位符）同样必须拦住。"""
        code, out, _ = run_preflight([self.hardcoded_weak])
        self.assert_code(1, code, out)
        self.assertIn("弱口令", out)

    def test_21_hardcoded_strong_password_is_not_flagged_and_not_printed(self):
        """硬编码强口令不该被拦；但也绝不能把口令内容打印进日志/终端。"""
        code, out, _ = run_preflight([self.hardcoded_strong])
        self.assert_code(0, code, out)
        self.assertNotIn("S3cret-Not-Weak", out)

    def test_22_placeholder_without_default_passes(self):
        """${TOOL_AUTH_PASSWORD}（无仓库内默认值）不该被判成弱口令。"""
        code, out, _ = run_preflight([self.no_default])
        self.assert_code(0, code, out)

    def test_23_weak_password_detection_is_not_dead_code(self):
        """守死代码本身：占位符默认值里的冒号后面不是右花括号，别再用 ':}' 切分。

        旧写法 `":}" in auth_line` 永远为假 —— 这条用例会把它钉住。
        """
        src = open(PREFLIGHT, encoding="utf-8").read()
        self.assertNotIn('":}" in auth_line', src,
                         "又用 ':}' 切分占位符了 —— 弱口令检测会退回死代码")


    # ── README 契约表一致性：这张表是脚本的对外契约 ──────────────────────
    def test_24_readme_contract_table_matches(self):
        """README 的「退出码契约」表必须与本文件的 CONTRACT 逐条一致。

        改脚本行为却忘了改表 → 这里失败；反之改了表但没改脚本 → 这里同样失败。
        """
        readme = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "README.md"))
        self.assertTrue(os.path.isfile(readme), "找不到 README：" + readme)
        with open(readme, encoding="utf-8") as fh:
            text = fh.read()
        self.assertIn("test_jar_preflight.py", text, "README 没写自测脚本怎么跑")
        for sid, desc, want_code in CONTRACT:
            row = None
            for line in text.splitlines():
                if line.strip().startswith("| " + sid + " "):
                    row = line
                    break
            self.assertIsNotNone(row, "README 契约表里缺少 %s 这一行" % sid)
            self.assertIn("| %d |" % want_code, row,
                          "README 里 %s 的期望退出码与本文件不一致（应为 %d）" % (sid, want_code))


if __name__ == "__main__":
    unittest.main(verbosity=2)
