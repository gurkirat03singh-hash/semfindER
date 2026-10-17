package semfind;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Walks a folder, and (re)indexes only new or changed files. */
public final class Indexer {
    private static final Set<String> EXT = Set.of("pdf", "docx", "txt", "md");

    private final Store store;
    private final Embedder embedder;

    public Indexer(Store store, Embedder embedder) {
        this.store = store;
        this.embedder = embedder;
    }

    /** @return number of files that were newly indexed or re-indexed */
    public int index(Path root, Consumer<String> log) throws Exception {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                String name = f.getFileName().toString();
                if (a.isRegularFile() && !name.startsWith("~$") && EXT.contains(ext(name))) files.add(f);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) {
                return FileVisitResult.CONTINUE; // skip unreadable folders instead of crashing
            }
        });

        Set<String> seen = new HashSet<>();
        int done = 0, changed = 0;
        for (Path f : files) {
            done++;
            String key = f.toAbsolutePath().normalize().toString();
            seen.add(key);
            try {
                long modified = Files.getLastModifiedTime(f).toMillis();
                long size = Files.size(f);
                if (store.isCurrent(key, modified, size)) continue;

                log.accept("(" + done + "/" + files.size() + ") " + f.getFileName());
                List<Store.Chunk> chunks = new ArrayList<>();
                for (TextExtractor.Page page : TextExtractor.extract(f)) {
                    for (String piece : Chunker.split(page.text())) {
                        chunks.add(new Store.Chunk(key, page.number(), piece, embedder.embed(piece)));
                    }
                }
                if (chunks.isEmpty()) log.accept("   no text found (scanned PDF? needs OCR): " + f.getFileName());
                store.replaceFile(key, modified, size, chunks); // empty files are recorded so they aren't retried
                changed++;
            } catch (Exception e) {
                log.accept("   skipped " + f.getFileName() + ": " + e.getMessage());
            }
        }

        // drop index entries for files deleted from this folder
        String prefix = root.toAbsolutePath().normalize().toString();
        for (String p : store.allPaths()) {
            if (p.startsWith(prefix) && !seen.contains(p)) store.delete(p);
        }
        return changed;
    }

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
