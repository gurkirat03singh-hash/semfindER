package semfind;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Brute-force cosine search over all chunks held in memory (fine up to ~100k chunks). */
public final class Searcher {
    public record Result(String path, int page, double score, String snippet) {}

    private final Store store;
    private final Embedder embedder;
    private volatile List<Store.Chunk> chunks = List.of();

    public Searcher(Store store, Embedder embedder) throws SQLException {
        this.store = store;
        this.embedder = embedder;
        reload();
    }

    public void reload() throws SQLException {
        this.chunks = store.loadAll();
    }

    /** Best-matching chunk per file, top files first. */
    public List<Result> search(String query, int maxFiles) {
        float[] q = embedder.embed(query);
        Map<String, Result> best = new HashMap<>();
        for (Store.Chunk c : chunks) {
            double s = dot(q, c.vec());
            Result cur = best.get(c.path());
            if (cur == null || s > cur.score()) {
                best.put(c.path(), new Result(c.path(), c.page(), s, c.text()));
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingDouble(Result::score).reversed())
                .limit(maxFiles)
                .toList();
    }

    private static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
