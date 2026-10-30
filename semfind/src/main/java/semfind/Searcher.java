package semfind;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Brute-force cosine search; ranks each file by its best-matching chunk. */
public final class Searcher {
    public record Result(String path, int page, double score, String snippet) {}

    static final double MIN_SCORE = 0.25;        // hide results below this similarity
    static final double RELATIVE_CUTOFF = 0.75;  // hide results below 75% of the top score
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

    public List<Result> search(String query, int maxResults) {
        float[] q = embedder.embed(query);
        Map<String, Result> bestByFile = new HashMap<>();
        for (Store.Chunk c : chunks) {
            double score = dot(q, c.vec());
            Result candidate = new Result(c.path(), c.page(), score * 100, c.text());
            Result best = bestByFile.get(c.path());
            if (best == null || candidate.score() > best.score()) {
                bestByFile.put(c.path(), candidate);
            }
        }

        List<Result> results = new ArrayList<>(bestByFile.values());
        results.sort(Comparator.comparingDouble(Result::score).reversed());
        if (results.isEmpty()) return results;

        double top = results.get(0).score();
        return results.stream()
                .filter(r -> r.score() >= MIN_SCORE * 100 && r.score() >= top * RELATIVE_CUTOFF)
                .limit(maxResults)
                .toList();
    }

    private static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}