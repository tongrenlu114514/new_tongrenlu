package info.tongrenlu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OriginalTagParser}.
 * 样例取自 m_track.original 的真实格式（THBWiki 匹配输出 + 网易云 alia 遗留数据）。
 */
class OriginalTagParserTest {

    private final OriginalTagParser parser = new OriginalTagParser();

    @Nested
    @DisplayName("空输入")
    class EmptyInputs {
        @Test
        void nullReturnsEmpty() {
            OriginalTagParser.ParsedOriginal p = parser.parse(null);
            assertThat(p.songs()).isEmpty();
            assertThat(p.games()).isEmpty();
        }

        @Test
        void blankReturnsEmpty() {
            OriginalTagParser.ParsedOriginal p = parser.parse("   \n  \n");
            assertThat(p.songs()).isEmpty();
            assertThat(p.games()).isEmpty();
        }
    }

    @Test
    @DisplayName("THBWiki 多行 '原曲 ／ 原作'：2 原曲 + 1 去重原作")
    void thbwikiMultiLine() {
        String original = "少女さとり　～ 3rd eye ／ 东方地灵殿　～ Subterranean Animism.\n"
                + "ハルトマンの妖怪少女 ／ 东方地灵殿　～ Subterranean Animism.";
        OriginalTagParser.ParsedOriginal p = parser.parse(original);
        assertThat(p.songs()).containsExactlyInAnyOrder(
                "少女さとり　～ 3rd eye", "ハルトマンの妖怪少女");
        assertThat(p.games()).containsExactly("东方地灵殿　～ Subterranean Animism.");
    }

    @Test
    @DisplayName("全角空格包围的分隔符同样能切分")
    void fullwidthSpaceAroundSlash() {
        OriginalTagParser.ParsedOriginal p = parser.parse("妖々跋扈　／　東方妖々夢");
        assertThat(p.songs()).containsExactly("妖々跋扈");
        assertThat(p.games()).containsExactly("東方妖々夢");
    }

    @ParameterizedTest(name = "[{index}] {0} -> 原曲 {1}")
    @CsvSource({
            "'原曲：永夜抄~Eastern Night.',          '永夜抄~Eastern Night.'",
            "'原曲:少女が見た日本の原風景',          '少女が見た日本の原風景'",
            "'Original : 星の器',                   '星の器'",
            "'[永]少女綺想曲 〜 Dream Battle',       '少女綺想曲 〜 Dream Battle'",
    })
    @DisplayName("单值前缀格式：1 原曲、0 原作")
    void singleSongPrefixes(String input, String expectedSong) {
        OriginalTagParser.ParsedOriginal p = parser.parse(input);
        assertThat(p.songs()).containsExactly(expectedSong);
        assertThat(p.games()).isEmpty();
    }

    @Test
    @DisplayName("(from X) → 1 原曲 + 1 原作")
    void fromParenthetical() {
        OriginalTagParser.ParsedOriginal p = parser.parse("おてんば恋娘 (from 東方紅魔郷)");
        assertThat(p.songs()).containsExactly("おてんば恋娘");
        assertThat(p.games()).containsExactly("東方紅魔郷");
    }

    @Test
    @DisplayName("'原作 より 原曲'（より 两侧有空白）→ 左原作、右原曲")
    void yoriSeparator() {
        OriginalTagParser.ParsedOriginal p = parser.parse("東方紅魔郷 より U.N.オーエンは彼女なのか？");
        assertThat(p.songs()).containsExactly("U.N.オーエンは彼女なのか？");
        assertThat(p.games()).containsExactly("東方紅魔郷");
    }

    @Test
    @DisplayName("曲名里的 より（无空白）不被误切，仍走 ／ 规则")
    void yoriInsideSongTitleNotSplit() {
        OriginalTagParser.ParsedOriginal p = parser.parse(
                "赤より紅い夢 ／ 东方红魔乡 ～ the Embodiment of Scarlet Devil.");
        assertThat(p.songs()).containsExactly("赤より紅い夢");
        assertThat(p.games()).containsExactly("东方红魔乡 ～ the Embodiment of Scarlet Devil.");
    }

    @Test
    @DisplayName("(YYYY 作品名より) 注记 → 只取作品名当原作")
    void yoriNote() {
        OriginalTagParser.ParsedOriginal p = parser.parse("(2021 東方虹龍洞より)");
        assertThat(p.songs()).isEmpty();
        assertThat(p.games()).containsExactly("東方虹龍洞");
    }

    @Test
    @DisplayName("裸半角斜杠列表：末段是歌名时不判原作")
    void bareSlashIsSongList() {
        OriginalTagParser.ParsedOriginal p = parser.parse("Original : デザイアドライブ / 古きユアンシェン");
        assertThat(p.songs()).containsExactly("デザイアドライブ", "古きユアンシェン");
        assertThat(p.games()).isEmpty();
    }

    @Test
    @DisplayName("裸半角斜杠列表：末段恰好是游戏名 → 原作，其余仍是原曲")
    void bareSlashTrailingGameName() {
        OriginalTagParser.ParsedOriginal p = parser.parse("竹取飛翔　～ Lunatic Princess / 東方永夜抄");
        assertThat(p.songs()).containsExactly("竹取飛翔　～ Lunatic Princess");
        assertThat(p.games()).containsExactly("東方永夜抄");
    }

    @Test
    @DisplayName("裸半角斜杠列表：含假名的「東方…」末段不判原作")
    void bareSlashTrailingKanaNotGame() {
        OriginalTagParser.ParsedOriginal p = parser.parse("デザイアドライブ / 東方の青い空");
        assertThat(p.songs()).containsExactly("デザイアドライブ", "東方の青い空");
        assertThat(p.games()).isEmpty();
    }

    @Test
    @DisplayName("尾缀引用的游戏名「東方星蓮船」→ 原作 + 原曲")
    void trailingQuotedGame() {
        OriginalTagParser.ParsedOriginal p = parser.parse("夜空のユーフォーロマンス「東方星蓮船」");
        assertThat(p.songs()).containsExactly("夜空のユーフォーロマンス");
        assertThat(p.games()).containsExactly("東方星蓮船");
    }

    @Test
    @DisplayName("尾缀引用叠加在裸斜杠列表之后 → 2 原曲 + 1 原作")
    void trailingQuotedGameAfterSlashList() {
        OriginalTagParser.ParsedOriginal p =
                parser.parse("感情の摩天楼 ～ Cosmic Mind / 平安のエイリアン「東方星蓮船」");
        assertThat(p.songs()).containsExactly("感情の摩天楼 ～ Cosmic Mind", "平安のエイリアン");
        assertThat(p.games()).containsExactly("東方星蓮船");
    }

    @Test
    @DisplayName("整行只有尾缀引用 → 只有原作")
    void wholeLineQuotedGame() {
        OriginalTagParser.ParsedOriginal p = parser.parse("「東方地霊殿」");
        assertThat(p.songs()).isEmpty();
        assertThat(p.games()).containsExactly("東方地霊殿");
    }

    @Test
    @DisplayName("尾缀引用剥离后剩余部分即使像游戏名也是原曲")
    void restAfterQuotedGameStaysSong() {
        OriginalTagParser.ParsedOriginal p = parser.parse("東方妖々夢　～ Ancient Temple「東方妖々夢」");
        assertThat(p.songs()).containsExactly("東方妖々夢　～ Ancient Temple");
        assertThat(p.games()).containsExactly("東方妖々夢");
    }

    @Test
    @DisplayName("整段是「游戏名 〜～ 副标题」→ 原作")
    void gameTitleWithSubtitle() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲\t東方妖々夢　～ Ancient Temple");
        assertThat(p.songs()).isEmpty();
        assertThat(p.games()).containsExactly("東方妖々夢　～ Ancient Temple");
    }

    @Test
    @DisplayName("裸游戏名样式（含假名 / 拉丁）不判原作")
    void bareGameLikeStaysSong() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲\t東方の青い空");
        assertThat(p.songs()).containsExactly("東方の青い空");
        assertThat(p.games()).isEmpty();
    }

    @Test
    @DisplayName("逆序记法「原曲2 東方虹龍洞 / 待ちわびた逢魔が時」→ 左原作右原曲")
    void kyokuGameSlashSong() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲2 東方虹龍洞 / 待ちわびた逢魔が時");
        assertThat(p.songs()).containsExactly("待ちわびた逢魔が時");
        assertThat(p.games()).containsExactly("東方虹龍洞");
    }

    @Test
    @DisplayName("〔原曲:…〕包裹 + 前缀嵌套 → 原曲")
    void sumiBracketWithPrefix() {
        OriginalTagParser.ParsedOriginal p = parser.parse("〔原曲:ハルトマンの妖怪少女〕");
        assertThat(p.songs()).containsExactly("ハルトマンの妖怪少女");
        assertThat(p.games()).isEmpty();
    }

    @Test
    @DisplayName("游戏名 – 原曲名 → 各自归位")
    void gameDashSong() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲：東方深秘録 – 竹林インフレイム");
        assertThat(p.songs()).containsExactly("竹林インフレイム");
        assertThat(p.games()).containsExactly("東方深秘録");
    }

    @Test
    @DisplayName("尾缀圆括号注记：曲名 (東方幻想郷)")
    void trailingParenGame() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲：少女綺想曲　～ Capriccio (東方幻想郷)");
        assertThat(p.songs()).containsExactly("少女綺想曲　～ Capriccio");
        assertThat(p.games()).containsExactly("東方幻想郷");
    }

    @Test
    @DisplayName("尾缀圆括号注记：括号内游戏名带副题")
    void trailingParenGameWithSubtitle() {
        OriginalTagParser.ParsedOriginal p =
                parser.parse("原曲：少女さとり　～ 3rd eye (東方地霊殿　～ Subterranean Animism.)");
        assertThat(p.songs()).containsExactly("少女さとり　～ 3rd eye");
        assertThat(p.games()).containsExactly("東方地霊殿　～ Subterranean Animism.");
    }

    @Test
    @DisplayName("尾缀圆括号注记：非東方开头的游戏名 + 副题")
    void trailingParenGameNotStartingWithTouhou() {
        OriginalTagParser.ParsedOriginal p =
                parser.parse("原曲：無間の鐘　～ Infinite Nightmare (ダブルスポイラー　～ 東方文花帖)");
        assertThat(p.songs()).containsExactly("無間の鐘　～ Infinite Nightmare");
        assertThat(p.games()).containsExactly("ダブルスポイラー　～ 東方文花帖");

        OriginalTagParser.ParsedOriginal p2 =
                parser.parse("原曲：真夜中のフェアリーダンス (妖精大戦争　～ 東方三月精)");
        assertThat(p2.songs()).containsExactly("真夜中のフェアリーダンス");
        assertThat(p2.games()).containsExactly("妖精大戦争　～ 東方三月精");
    }

    @Test
    @DisplayName("尾缀圆括号注记：纯汉字题 + 拉丁副题")
    void trailingParenKanjiTitleLatinSubtitle() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲：天空のグリニッジ (大空魔術　～ Magical Astronomy)");
        assertThat(p.songs()).containsExactly("天空のグリニッジ");
        assertThat(p.games()).containsExactly("大空魔術　～ Magical Astronomy");
    }

    @Test
    @DisplayName("裸斜杠末段：游戏名 + ASCII ~ 副题")
    void bareSlashTrailingGameWithAsciiTilde() {
        OriginalTagParser.ParsedOriginal p =
                parser.parse("原曲:亡き王女の為のセプテット / 東方紅魔郷~ the Embodiment of Scarlet Devil.");
        assertThat(p.songs()).containsExactly("亡き王女の為のセプテット");
        assertThat(p.games()).containsExactly("東方紅魔郷~ the Embodiment of Scarlet Devil.");
    }

    @Test
    @DisplayName("波浪线连接记法 + 尾缀社团名：曲名 ～ゲーム名 ～ 副題～[社团]")
    void tildeConnectorWithCircleTag() {
        OriginalTagParser.ParsedOriginal p = parser.parse(
                "原曲: 時代親父とハイカラ少女 ～東方星蓮船 ～ Undefined Fantastic Object.～[石鹸屋]");
        assertThat(p.songs()).containsExactly("時代親父とハイカラ少女");
        assertThat(p.games()).containsExactly("東方星蓮船 ～ Undefined Fantastic Object.");
    }

    @Test
    @DisplayName("波浪线连接记法：曲名带顿号列表仍能各自拆出")
    void tildeConnectorWithCommaSong() {
        OriginalTagParser.ParsedOriginal p = parser.parse(
                "原曲: 月まで届け、不死の煙 ～東方永夜抄 ～ Imperishable Night.～[SWING HOLIC]");
        assertThat(p.songs()).containsExactly("月まで届け", "不死の煙");
        assertThat(p.games()).containsExactly("東方永夜抄 ～ Imperishable Night.");
    }

    @Test
    @DisplayName("尾缀方括号紧跟游戏名 → 原作")
    void trailingBracketGame() {
        OriginalTagParser.ParsedOriginal p = parser.parse("原曲：古きユアンシェン [東方神霊廟]");
        assertThat(p.songs()).containsExactly("古きユアンシェン");
        assertThat(p.games()).containsExactly("東方神霊廟");
    }

    @Test
    @DisplayName("尾缀方括号社团名跟在波浪线连接后 → 丢弃，不误判成游戏")
    void trailingBracketCircleAfterTildeNotGame() {
        OriginalTagParser.ParsedOriginal p = parser.parse(
                "原曲: ハルトマンの妖怪少女 ～東方地霊殿 ～ Subterranean Animism.～[東方事変]");
        assertThat(p.songs()).containsExactly("ハルトマンの妖怪少女");
        assertThat(p.games()).containsExactly("東方地霊殿 ～ Subterranean Animism.");
    }

    @Test
    @DisplayName("波浪线连接记法：无社团名、尾缀悬挂 ～")
    void tildeConnectorDanglingTilde() {
        OriginalTagParser.ParsedOriginal p = parser.parse(
                "原曲: ルーネイトエルフ ～東方紅魔郷　～ the Embodiment of Scarlet Devil.～");
        assertThat(p.songs()).containsExactly("ルーネイトエルフ");
        assertThat(p.games()).containsExactly("東方紅魔郷　～ the Embodiment of Scarlet Devil.");
    }

    @Test
    @DisplayName("网易云 alia 逗号列表 → 多个原曲")
    void commaSeparatedAliases() {
        OriginalTagParser.ParsedOriginal p = parser.parse("ネクロファンタジア, 幽霊楽団　～ Phantom Ensemble");
        assertThat(p.songs()).containsExactly("ネクロファンタジア", "幽霊楽団　～ Phantom Ensemble");
        assertThat(p.games()).isEmpty();
    }

    @Test
    @DisplayName("重复解析同一输入结果一致（幂等）")
    void idempotent() {
        String original = "少女さとり　～ 3rd eye ／ 东方地灵殿\n少女さとり　～ 3rd eye ／ 东方地灵殿";
        OriginalTagParser.ParsedOriginal first = parser.parse(original);
        OriginalTagParser.ParsedOriginal second = parser.parse(original);
        assertThat(first.songs()).isEqualTo(second.songs()).hasSize(1);
        assertThat(first.games()).isEqualTo(second.games()).hasSize(1);
    }

    @Test
    @DisplayName("超长片段被丢弃，不写入 m_tag.tag")
    void overlongSegmentSkipped() {
        String tooLong = "あ".repeat(300);
        OriginalTagParser.ParsedOriginal p = parser.parse(tooLong);
        assertThat(p.songs()).isEmpty();
        assertThat(p.games()).isEmpty();
    }
}
