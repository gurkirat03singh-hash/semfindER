package semfind;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;

/**
 * Usage:
 *   java -jar semfind-1.0.0.jar                      -> opens the window
 *   java -jar semfind-1.0.0.jar index  C:\Notes      -> index a folder from the command line
 *   java -jar semfind-1.0.0.jar search photosynthesis in plants
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        Path dbFile = Path.of(System.getProperty("user.home"), ".semfind", "index.db");

        Store store = new Store(dbFile);
        store.ensureModel(Embedder.MODEL_NAME);
        Embedder embedder = new Embedder();
        Indexer indexer = new Indexer(store, embedder);
        Searcher searcher = new Searcher(store, embedder);

        if (args.length == 0 || args[0].equals("ui")) {
            SwingUtilities.invokeLater(() -> new SearchApp(indexer, searcher).setVisible(true));
            return;
        }

        switch (args[0]) {
            case "index" -> {
                if (args.length < 2) { System.out.println("Usage: index <folder>"); return; }
                int n = indexer.index(Path.of(args[1]), System.out::println);
                System.out.println("Done. " + n + " new/changed file(s) indexed.");
                store.close();
            }
            case "search" -> {
                if (args.length < 2) { System.out.println("Usage: search <query>"); return; }
                String q = String.join(" ", List.of(args).subList(1, args.length));
                for (Searcher.Result r : searcher.search(q, 10)) {
                    System.out.printf("%.3f  %s  (p.%d)%n        %s%n", r.score(), r.path(), r.page(),
                            r.snippet().substring(0, Math.min(160, r.snippet().length())).replace('\n', ' ') + "...");
                }
                store.close();
            }
            default -> System.out.println("Commands: ui | index <folder> | search <query>");
        }
    }
}
