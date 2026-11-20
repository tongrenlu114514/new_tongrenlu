package info.tongrenlu.support;

import java.util.regex.Pattern;

/**
 * 展会名 → THBWiki 系列总览页。
 * <p>
 * THBWiki 上通常只有系列页（Comic Market、博丽神社例大祭等），没有单届页面；
 * 未知展会返回 {@code null}，宁可没有来源也不编造链接。
 * 规则与前端 {@code wiki.js#thbwikiEventUrl} 保持一致（那边未知时回落「活动导航」，
 * 这里只用于 sources 标注，因此不做兜底）。
 */
public final class EventSeriesUrls {

    private static final String BASE = "https://thbwiki.cc/";

    private static final Pattern COMIC_MARKET_NUM = Pattern.compile("^C\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMIC_MARKET_WORD =
            Pattern.compile("comic\\s*market|comiket|comike", Pattern.CASE_INSENSITIVE);
    private static final Pattern CM = Pattern.compile("\\bCM\\d*\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMICUP = Pattern.compile("comicup", Pattern.CASE_INSENSITIVE);
    private static final Pattern CP = Pattern.compile("\\bCP\\d*\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TH_ONLY = Pattern.compile("th\\s*only", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHANGHAI_TH = Pattern.compile("上海\\s*TH\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern RTS = Pattern.compile("\\bRTS\\d*\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RED_NUM = Pattern.compile("红\\d");
    private static final Pattern M3_HEAD = Pattern.compile("^M3", Pattern.CASE_INSENSITIVE);
    private static final Pattern M3_FULL =
            Pattern.compile("music\\s*media-mix\\s*market", Pattern.CASE_INSENSITIVE);

    private EventSeriesUrls() {
    }

    /**
     * @param name 展会名（m_tag.tag）
     * @return 对应系列页 URL；无法识别时返回 null
     */
    public static String thbwikiUrl(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.strip();
        if (COMIC_MARKET_NUM.matcher(n).find() || COMIC_MARKET_WORD.matcher(n).find()
                || CM.matcher(n).find() || n.contains("コミケ") || n.contains("コミックマーケット")
                || n.contains("こみっくまーけっと")) {
            return BASE + "Comic_Market";
        }
        if (COMICUP.matcher(n).find() || n.contains("魔都同人祭") || CP.matcher(n).find()) {
            return BASE + "COMICUP";
        }
        if (n.contains("魅知") || n.contains("魅博")) {
            return BASE + "魅知幻想博览会";
        }
        if (TH_ONLY.matcher(n).find() || SHANGHAI_TH.matcher(n).find()) {
            return BASE + "上海THONLY";
        }
        if (n.contains("例大祭") || n.contains("れいたいさい") || RTS.matcher(n).find()) {
            return BASE + "博丽神社例大祭";
        }
        if (n.contains("紅楼夢") || n.contains("红楼梦") || n.contains("こうろうむ")
                || RED_NUM.matcher(n).find()) {
            return BASE + "东方红楼梦";
        }
        if (M3_HEAD.matcher(n).find() || n.contains("M3") || M3_FULL.matcher(n).find()) {
            return BASE + "M3";
        }
        return null;
    }
}
