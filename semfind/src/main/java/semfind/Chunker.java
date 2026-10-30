package semfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits text into overlapping word windows.
 * 120 words is about 160-180 tokens, which stays under MiniLM's 256-token limit
 * (longer chunks get silently truncated by the model).
 */
public final class Chunker {
    static final int SIZE = 120;
    static final int OVERLAP = 30;
    static final int MIN_WORDS = 2;

    private Chunker() {}

    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        String[] words = text.trim().split("\\s+");
        int step = SIZE - OVERLAP;
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + SIZE, words.length);
            if (end - start >= MIN_WORDS) {
                out.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            }
            if (end == words.length) break;
        }
        return out;
    }
}
