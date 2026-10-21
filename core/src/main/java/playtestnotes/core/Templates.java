package playtestnotes.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/** Token expansion for the line and file-name templates, time formats and file-name sanitising. */
public final class Templates {
    private Templates() {}

    /**
     * Replaces each {@code {token}} whose name is in {@code values}. Unknown tokens and stray braces stay as
     * written, so a template may contain {@code {}} or {@code {notatoken}} literally.
     */
    public static String expand(String template, Map<String, String> values) {
        StringBuilder out = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String name = template.substring(i + 1, end);
                    String v = values.get(name);
                    if (v != null) {
                        out.append(v);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * A name that is safe as a file name on Windows and elsewhere: {@code <>:"/\|?*} and control characters
     * become {@code _}; letters in any script are kept. Trailing dots and spaces (which Windows drops) become
     * {@code _} too, and an empty name becomes {@code _}.
     */
    public static String sanitize(String name) {
        StringBuilder b = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            if (cp < 0x20 || cp == 0x7f || "<>:\"/\\|?*".indexOf(cp) >= 0) b.append('_');
            else b.appendCodePoint(cp);
        });
        int end = b.length();
        while (end > 0 && (b.charAt(end - 1) == '.' || b.charAt(end - 1) == ' ')) b.setCharAt(--end, '_');
        return b.isEmpty() ? "_" : b.toString();
    }

    /** The file name for a note: the template with each token's value sanitised (a world name can't add a folder). */
    public static String fileName(String template, String date, String world, String server, String player) {
        return expand(template, Map.of("date", sanitize(date), "world", sanitize(world), "server", sanitize(server), "player", sanitize(player)));
    }

    public static ZoneId zone(boolean utc) {
        return utc ? ZoneOffset.UTC : ZoneId.systemDefault();
    }

    /** {@code yyyy-MM-dd} in the chosen zone. */
    public static String date(Instant t, boolean utc) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(t.atZone(zone(utc)));
    }

    /** The time of a note in one of {@link Settings#TIME_FORMATS}. */
    public static String time(Instant t, String format, boolean utc) {
        return switch (format) {
            case "epoch-ms" -> Long.toString(t.toEpochMilli());
            case "ISO-8601" -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t.truncatedTo(ChronoUnit.SECONDS).atZone(zone(utc)));
            default -> DateTimeFormatter.ofPattern(format, Locale.ENGLISH).format(t.atZone(zone(utc)));
        };
    }

    /** A coordinate with {@code decimals} places, rounded down (0 decimals gives the block coordinate). */
    public static String coordinate(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
        return new BigDecimal(v).setScale(decimals, RoundingMode.FLOOR).toPlainString();
    }
}
