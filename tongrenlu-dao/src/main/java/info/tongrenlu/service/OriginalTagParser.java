package info.tongrenlu.service;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 {@code m_track.original} 的自由文本拆成「原曲」(song / THBWiki ogmusic) 与
 * 「原作」(game / 原曲出处) 两个集合，供 {@link OriginalTagService} 写入 {@code m_tag}。
 *
 * <p>纯解析、无 Spring 依赖，便于单测。格式来源有两类：
 * <ul>
 *   <li>THBWiki 匹配任务 ({@code ThbwikiService.assignOriginalInfo}) 写入的多行文本，
 *       每行 {@code "原曲名 ／ 原作名"}，分隔符是全角 ／（两侧有空格）。</li>
 *   <li>网易云 alia / 历史导入的遗留单行文本，如 {@code 原曲：永夜抄~Eastern Night.}、
 *       {@code おてんば恋娘 (from 東方紅魔郷)}、{@code 妖々跋扈/東方妖々夢}、
 *       {@code Original : 星の器}、{@code [永]少女綺想曲 〜 Dream Battle}、
 *       {@code 東方紅魔郷 より U.N.オーエンは彼女なのか？}、
 *       {@code (2021 東方虹龍洞より)}。</li>
 * </ul>
 *
 * <p>保守规则：只有全角 ／、{@code 原作 より 原曲}（より 两侧须有空白）、
 * {@code (YYYY 作品名より)} 和 {@code (from X)} 能产生「原作」；裸 {@code /}、
 * {@code 、}、{@code ,} 一律当作多首原曲，不猜哪一段是游戏。
 *
 * <p>例外：游戏名是高度可识别的固定样式（{@code 東方} + 纯汉字），以下格式直接判原作：
 * <ul>
 *   <li>尾缀引用：{@code 曲名「東方星蓮船」}（网易云 alia 常见，可叠加在斜杠列表之后）。</li>
 *   <li>尾缀圆括号：{@code 曲名 (東方地霊殿　～ Subterranean Animism.)}、
 *       {@code 曲名 (ダブルスポイラー　～ 東方文花帖)}、{@code 曲名 (大空魔術　～ Magical Astronomy)}。</li>
 *   <li>波浪线连接：{@code 曲名 ～東方星蓮船 ～ Undefined Fantastic Object.～[石鹸屋]}，
 *       尾缀 {@code [社团名]} 丢弃；若 {@code [東方…]} 前面不是 ～ 则视为原作。</li>
 *   <li>裸斜杠列表的末段是游戏名（可带副题）：{@code 竹取飛翔　～ Lunatic Princess / 東方永夜抄}。</li>
 *   <li>整段是游戏名（可带副题）：{@code 東方妖々夢　～ Ancient Temple}；
 *       但「尾缀引用」剥离后的剩余部分不适用（剩余部分按定义是原曲）。</li>
 *   <li>逆序记法：{@code 原曲2 東方虹龍洞 / 待ちわびた逢魔が時}。</li>
 * </ul>
 */
public class OriginalTagParser {

    /** 单条 tag 允许的最大长度，避免把整段说明塞进 m_tag.tag */
    private static final int MAX_TAG_LENGTH = 255;

    /** THBWiki 写入的分隔符：全角 ／，两侧可能是 ASCII 空格或全角空格 */
    private static final Pattern FULLWIDTH_SLASH = Pattern.compile("[\\s　]*／[\\s　]*");

    /** "(from X)" / "（from X）" → X 视为原作 */
    private static final Pattern FROM_PATTERN =
            Pattern.compile("\\s*[(（]from\\s+([^)）]*)[)）]\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * 游戏名样式：東方 + 纯汉字（含 々），不含假名 / 拉丁字母，
     * 避免把「東方の青い空」「東方Project」这类歌名 / 标识误判成游戏。
     */
    private static final String GAME_NAME = "[東东]方[\\u3005\\u4e00-\\u9fff]{2,6}";

    /** 波浪线连接符：〜 (U+301C) / ～ (U+FF5E) / ~ (ASCII)，网易云 alia 三种混用 */
    private static final String TILDE = "[\\u301C\\uFF5E~]";

    /** 尾缀引用的游戏名：曲名「東方星蓮船」 / 曲名『東方紅魔郷』（网易云 alia 常见） */
    private static final Pattern TRAILING_QUOTED_GAME =
            Pattern.compile("[\\s　]*[「『](" + GAME_NAME + ")[」』]\\s*$");

    /** 尾缀圆括号注记：曲名 (東方地霊殿　～ Subterranean Animism.)，内容是否像游戏在代码里判断 */
    private static final Pattern TRAILING_PAREN =
            Pattern.compile("\\s*[(（]([^)（]*)[)）]\\s*$");

    /** 尾缀方括号注记：曲名 [東方神霊廟]（游戏）或 曲名～…～[石鹸屋]（社团名，丢弃） */
    private static final Pattern TRAILING_BRACKET =
            Pattern.compile("\\s*\\[([^\\]]{1,40})\\]\\s*$");

    /** 「曲名 ～ゲーム名 ～ 副題」连接记法：曲名 ～東方星蓮船 ～ Undefined Fantastic Object.～ */
    private static final Pattern TRAILING_TILDE_GAME = Pattern.compile(
            "[\\s　]*" + TILDE + "[\\s　]*(" + GAME_NAME + "(?:[\\s　]*" + TILDE + "[^" + TILDE + "]*)?)" + TILDE + "?[\\s　]*$");

    /** 逆序记法：原曲2 東方虹龍洞 / 待ちわびた逢魔が時 → 左侧游戏、右侧原曲 */
    private static final Pattern KYOKU_GAME_SLASH_SONG =
            Pattern.compile("^原曲\\d*[\\s　]+(" + GAME_NAME + ")[\\s　]*/[\\s　]*(.+)$");

    /** 裸斜杠列表的末段是游戏名（可带副题）：東方紅魔郷 / 東方紅魔郷~ the Embodiment of Scarlet Devil. */
    private static final Pattern SEGMENT_GAME_NAME =
            Pattern.compile("^" + GAME_NAME + "([\\s　]*" + TILDE + ".*" + ")?$");

    /** 整段是游戏名 + 副标题：東方妖々夢　～ Ancient Temple（裸游戏名不算，防误伤歌名） */
    private static final Pattern GAME_TITLE_WITH_SUBTITLE =
            Pattern.compile("^" + GAME_NAME + "[\\s　]*" + TILDE + ".+$");

    /** 游戏名 – 原曲名：東方深秘録 – 竹林インフレイム */
    private static final Pattern GAME_DASH_SONG =
            Pattern.compile("^(" + GAME_NAME + ")[\\s　]*[–—][\\s　]*(.+)$");

    /**
     * "原作 より 原曲"（日文 from 记法）。より 两侧必须有空白，避免误伤
     * 「赤より紅い夢」「紅より儚い永遠」这类把 より 当作曲名一部分的情况。
     */
    private static final Pattern YORI_SEPARATOR = Pattern.compile("[\\s　]+より[\\s　]+");

    /** "(2021 東方虹龍洞より)" 之类出处注记 → 括号内的作品名视为原作 */
    private static final Pattern YORI_NOTE =
            Pattern.compile("^[(（]?\\s*\\d{4}\\s+(.+?)\\s*より\\s*[)）]?$");

    /** 列表分隔符：顿号 / 逗号 / 半角斜杠（半角斜杠右侧仍当作原曲，不当原作） */
    private static final Pattern LIST_SEPARATOR = Pattern.compile("\\s*(?:、|,|，|/)\\s*");

    /** 前缀规则，按「最长/最具体优先」排列，与 OriginalUrlBackfillService.PREFIX_RULES 对齐 */
    private static final Pattern[] PREFIX_RULES = {
            Pattern.compile("^原曲[,、]原詩[:：]\\s*"),
            Pattern.compile("^原曲・原詩[:：]\\s*"),
            Pattern.compile("^(?i)original\\s*[:\\s]?\\s*"),
            Pattern.compile("^原曲：\\s*"),
            Pattern.compile("^原曲\\s*[:\\s\\t]\\s*"),
            Pattern.compile("^原曲[,，]\\s*"),
            Pattern.compile("^原曲\\.\\s*"),
            Pattern.compile("^原曲[・]\\s*"),
            Pattern.compile("^\\[[^\\]]{1,6}\\]\\s*"),   // [永]xxx 之类短前缀
            Pattern.compile("^原曲(?=\\S)"),              // 裸 原曲 前缀，兜底
    };

    /** 包裹整段歌名的成对符号 */
    private static final char[][] BRACKET_PAIRS = {
            {'《', '》'}, {'「', '」'}, {'『', '』'}, {'〔', '〕'}, {'【', '】'}, {'〖', '〗'},
            {'(', ')'}, {'（', '）'}, {'[', ']'},
    };

    /** 解析结果，保持插入顺序以便 preview / 日志可读 */
    public record ParsedOriginal(LinkedHashSet<String> songs, LinkedHashSet<String> games) {}

    public ParsedOriginal parse(String original) {
        LinkedHashSet<String> songs = new LinkedHashSet<>();
        LinkedHashSet<String> games = new LinkedHashSet<>();
        if (original == null || original.isBlank()) {
            return new ParsedOriginal(songs, games);
        }
        for (String rawLine : original.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (!line.isEmpty()) {
                parseLine(line, songs, games);
            }
        }
        return new ParsedOriginal(songs, games);
    }

    private void parseLine(String line, LinkedHashSet<String> songs, LinkedHashSet<String> games) {
        // 0) 尾缀引用的游戏名：曲名「東方星蓮船」→ 原作，剩余部分继续当原曲解析
        boolean strippedQuotedGame = false;
        Matcher quoted = TRAILING_QUOTED_GAME.matcher(line);
        if (quoted.find()) {
            addGame(normalize(quoted.group(1)), games);
            strippedQuotedGame = true;
            line = line.substring(0, quoted.start()).strip();
            if (line.isEmpty()) {
                return;
            }
        }

        // 0.5) "原曲2 東方虹龍洞 / 待ちわびた逢魔が時" → 左侧原作、右侧原曲
        Matcher kyoku = KYOKU_GAME_SLASH_SONG.matcher(line);
        if (kyoku.matches()) {
            addGame(normalize(kyoku.group(1)), games);
            addSong(normalize(kyoku.group(2)), songs);
            return;
        }

        // 1) 全角 ／ → 左侧原曲、右侧原作（THBWiki 格式）
        Matcher fw = FULLWIDTH_SLASH.matcher(line);
        if (fw.find()) {
            addSong(normalize(line.substring(0, fw.start())), songs);
            addGame(normalize(line.substring(fw.end())), games);
            return;
        }

        // 2) "原作 より 原曲" → 左侧原作、右侧原曲（方向与 ／ 相反）
        Matcher yori = YORI_SEPARATOR.matcher(line);
        if (yori.find()) {
            addGame(normalize(line.substring(0, yori.start())), games);
            addSong(normalize(line.substring(yori.end())), songs);
            return;
        }

        // 3) "(2021 東方虹龍洞より)" 之类注记 → 只取作品名当原作
        Matcher note = YORI_NOTE.matcher(line);
        if (note.matches()) {
            addGame(normalize(note.group(1)), games);
            return;
        }

        // 3.5) 尾缀圆括号注记的原作：曲名 (東方地霊殿　～ Subterranean Animism.)
        Matcher paren = TRAILING_PAREN.matcher(line);
        if (paren.find() && isGameLike(paren.group(1).strip())) {
            addGame(normalize(paren.group(1)), games);
            line = line.substring(0, paren.start()).strip();
            if (line.isEmpty()) {
                return;
            }
        }

        // 3.6) 尾缀方括号：紧跟游戏名（且前面不是 ～）→ 原作；社团名 [石鹸屋] → 丢弃
        Matcher bracket = TRAILING_BRACKET.matcher(line);
        if (bracket.find()) {
            String content = bracket.group(1).strip();
            boolean isGame = !precededByTilde(line, bracket.start()) && isGameLike(content);
            line = line.substring(0, bracket.start()).strip();
            if (isGame) {
                addGame(normalize(content), games);
            }
            if (line.isEmpty()) {
                return;
            }
        }

        // 3.7) 「曲名 ～ゲーム名 ～ 副題」连接记法 → 尾段是原作，前面是原曲
        Matcher tilde = TRAILING_TILDE_GAME.matcher(line);
        if (tilde.find()) {
            addGame(normalize(tilde.group(1)), games);
            line = line.substring(0, tilde.start()).strip();
            if (line.isEmpty()) {
                return;
            }
        }

        // 4) "(from X)" → X 原作，去掉括号后的剩余文本继续当原曲
        Matcher from = FROM_PATTERN.matcher(line);
        if (from.find()) {
            addGame(normalize(from.group(1)), games);
            line = line.substring(0, from.start()).strip();
            if (line.isEmpty()) {
                return;
            }
        }

        // 5) 去前缀 / 去包裹符号，再按列表分隔符切成多首原曲
        String text = normalize(line);

        // 整段是「游戏名 〜～ 副标题」→ 原作（尾缀引用剥离后的剩余部分不适用，剩余部分按定义是原曲）
        if (!strippedQuotedGame && GAME_TITLE_WITH_SUBTITLE.matcher(text).matches()) {
            addGame(text, games);
            return;
        }

        // "東方深秘録 – 竹林インフレイム" → 原作 – 原曲
        Matcher dash = GAME_DASH_SONG.matcher(text);
        if (dash.matches()) {
            addGame(dash.group(1), games);
            addSong(dash.group(2), songs);
            return;
        }

        String[] segments = LIST_SEPARATOR.split(text);
        // 裸斜杠列表的末段是游戏名（可带副题）→ 原作（其余段仍是多首原曲）
        if (segments.length >= 2
                && SEGMENT_GAME_NAME.matcher(segments[segments.length - 1]).matches()) {
            addGame(segments[segments.length - 1], games);
            segments = java.util.Arrays.copyOf(segments, segments.length - 1);
        }
        for (String segment : segments) {
            addSong(segment, songs);
        }
    }

    /**
     * 圆括号内容是否像「原作」：
     * <ol>
     *   <li>東方 + 纯汉字（可带副题）：{@code 東方幻想郷}、{@code 東方地霊殿　～ Subterranean Animism.}</li>
     *   <li>含東方/东方且含波浪线副题：{@code ダブルスポイラー　～ 東方文花帖}、{@code 妖精大戦争　～ 東方三月精}</li>
     *   <li>纯汉字题 + 波浪线 + 拉丁副题：{@code 大空魔術　～ Magical Astronomy}</li>
     * </ol>
     */
    private boolean isGameLike(String content) {
        if (content.matches(GAME_NAME + "([\\s　]*" + TILDE + ".*)?")) {
            return true;
        }
        boolean hasTouhou = content.contains("東方") || content.contains("东方");
        if (hasTouhou && content.matches(".*[\\s　]*" + TILDE + ".*")) {
            return true;
        }
        return content.matches("[\\u3005\\u4e00-\\u9fff]{2,8}[\\s　]*" + TILDE
                + "[\\s　]*[A-Za-z0-9][A-Za-z0-9 .&,!'\\-]*$");
    }

    /** pos 前面（跳过空白）是否是波浪线连接符，用于区分「曲名 [東方神霊廟]」和「曲名～…～[社团名]」 */
    private boolean precededByTilde(String line, int pos) {
        int i = pos - 1;
        while (i >= 0 && (line.charAt(i) == ' ' || line.charAt(i) == '　')) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        char c = line.charAt(i);
        return c == '〜' || c == '～' || c == '~';
    }

    /** 去前缀（可重复）、去成对包裹符号，返回 trim 后的文本 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String value = text.strip();
        if (value.isEmpty()) {
            return "";
        }
        // 反复剥离前缀和成对包裹符号，直到稳定；兼容「原曲：原曲:xxx」「〔原曲:xxx〕」之类嵌套
        while (true) {
            String stripped = stripPrefix(value);
            if (!stripped.equals(value)) {
                value = stripped.strip();
                if (value.isEmpty()) {
                    return "";
                }
                continue;
            }
            String unwrapped = stripOuterBracket(value);
            if (!unwrapped.equals(value)) {
                value = unwrapped;
                if (value.isEmpty()) {
                    return "";
                }
                continue;
            }
            break;
        }
        return value;
    }

    /** 仅当整段被成对符号包裹时剥离一层（保留内部内容） */
    private String stripOuterBracket(String value) {
        for (char[] pair : BRACKET_PAIRS) {
            if (value.length() >= 2
                    && value.charAt(0) == pair[0]
                    && value.charAt(value.length() - 1) == pair[1]) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }

    private String stripPrefix(String text) {
        for (Pattern rule : PREFIX_RULES) {
            Matcher m = rule.matcher(text);
            if (m.find()) {
                return m.replaceFirst("");
            }
        }
        return text;
    }

    private void addSong(String value, LinkedHashSet<String> songs) {
        String v = clean(value);
        if (v != null) {
            songs.add(v);
        }
    }

    private void addGame(String value, LinkedHashSet<String> games) {
        String v = clean(value);
        if (v != null) {
            games.add(v);
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        if (v.isEmpty() || v.length() > MAX_TAG_LENGTH) {
            return null;
        }
        return v;
    }
}
