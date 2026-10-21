package playtestnotes.core;

import java.util.ArrayList;
import java.util.List;

/** Splits a note into its text and its known {@code #tags}. */
public final class NoteParser {
    private NoteParser() {}

    /** The note without its known tags, and the tags found (as listed in the settings, in order of appearance). */
    public record Parsed(String text, List<String> tags) {
        /** The tags as written into the file: {@code #bug #idea}. */
        public String tagText() {
            return String.join(" ", tags.stream().map(t -> "#" + t).toList());
        }
    }

    /**
     * Words that start with {@code #} and match a known tag (ignoring case and trailing punctuation) are taken
     * out of the text. Unknown {@code #words} stay in the text. Runs of spaces collapse to one.
     */
    public static Parsed parse(String raw, List<String> known) {
        List<String> words = new ArrayList<>(), tags = new ArrayList<>();
        for (String w : raw.strip().split("\\s+")) {
            if (w.isEmpty()) continue;
            String tag = w.length() > 1 && w.charAt(0) == '#' ? match(trimPunctuation(w.substring(1)), known) : null;
            if (tag == null) words.add(w);
            else if (!tags.contains(tag)) tags.add(tag);
        }
        return new Parsed(String.join(" ", words), List.copyOf(tags));
    }

    private static String match(String word, List<String> known) {
        for (String k : known) if (k.equalsIgnoreCase(word)) return k;
        return null;
    }

    private static String trimPunctuation(String w) {
        int end = w.length();
        while (end > 0 && ".,;:!?)".indexOf(w.charAt(end - 1)) >= 0) end--;
        return w.substring(0, end);
    }

    /** Length in characters as a person counts them (code points, so CJK and emoji count once). */
    public static int length(String s) {
        return s.codePointCount(0, s.length());
    }
}
