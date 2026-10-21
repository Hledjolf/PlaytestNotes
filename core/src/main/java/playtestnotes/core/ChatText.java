package playtestnotes.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The chat form of notes. The file is Markdown; chat can't show it, so players would see literal asterisks.
 * Text a player typed is never run through a stripper when the line is made: {@link #plainTemplate} removes the
 * markers from the <em>template</em> before the tokens are filled in. {@link #plain} is for lines read back
 * from the file ({@code /notes}), where the template is no longer known, and strips only unambiguous emphasis.
 * Everything produced here uses characters the game font has (code points up to U+04FF).
 */
public final class ChatText {
    private ChatText() {}

    private static final Pattern STAR_STAR = Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*");
    private static final Pattern UNDER_UNDER = Pattern.compile("(?<![\\w])__(?=\\S)(.+?)(?<=\\S)__(?!\\w)");
    private static final Pattern STAR = Pattern.compile("(?<![\\w*])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![\\w*])");
    private static final Pattern UNDER = Pattern.compile("(?<![\\w])_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?!\\w)");
    private static final Pattern TICKS = Pattern.compile("`([^`]+)`");
    private static final Pattern LIST_MARK = Pattern.compile("^\\s*[-*+]\\s+");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");

    /** A line template with its Markdown formatting removed, for the chat copy of a note. */
    public static String plainTemplate(String template) {
        return stripEmphasis(LIST_MARK.matcher(template).replaceFirst(""));
    }

    /** The chat line for a note: the template without its formatting, filled in with the same values as the file's line. */
    public static String line(String template, Map<String, String> values) {
        return Templates.expand(plainTemplate(template), values).strip();
    }

    /**
     * A line read back from the notes file, as chat text; null for lines that carry nothing (rules, the table
     * separator). Strips emphasis pairs and backticks only where they sit at word edges, so {@code 2*3} and
     * {@code snake_case} survive.
     */
    public static String plain(String markdownLine) {
        // Files written before 1.0.0 used "→", which the game font lacks.
        String s = markdownLine.strip().replace("→", "->");
        if (s.isEmpty() || RULE.matcher(s).matches()) return null;
        if (s.startsWith("|")) return tableRow(s);
        var h = HEADING.matcher(s);
        if (h.matches()) return "[" + stripEmphasis(h.group(1).strip()) + "]";
        return stripEmphasis(LIST_MARK.matcher(s).replaceFirst("")).strip();
    }

    /** {@link #plain} over many lines, dropping the nulls. */
    public static List<String> plainAll(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            String p = plain(l);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static String tableRow(String s) {
        String body = s.replace("\\|", "\u0001");
        if (body.replaceAll("[|\\-:\\s]", "").isEmpty()) return null;
        List<String> cells = new ArrayList<>();
        for (String c : body.split("\\|")) {
            String t = c.strip().replace('\u0001', '|');
            if (!t.isEmpty()) cells.add(t);
        }
        return cells.isEmpty() ? null : String.join(" - ", cells);
    }

    private static String stripEmphasis(String s) {
        s = STAR_STAR.matcher(s).replaceAll("$1");
        s = UNDER_UNDER.matcher(s).replaceAll("$1");
        s = STAR.matcher(s).replaceAll("$1");
        s = UNDER.matcher(s).replaceAll("$1");
        return TICKS.matcher(s).replaceAll("$1");
    }
}
