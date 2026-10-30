# SemFind - meaning-based local file search (Java)

Search your PDFs, PPT, DOCX, TXT and MD files by meaning, fully offline.
Searching "notes about photosynthesis" finds the right file even if those words aren't in it.

## Build and run
1. Install JDK 25+ and Apache Maven.
2. In this folder: `mvn package`
3. Run: `java -jar target/semfind-1.0.0.jar`
   - Click **Add folder...** to index, then type a query.
   - CLI: `... index C:\Notes` and `... search your query here`

The index lives in `%USERPROFILE%\.semfind\index.db`. Re-running index only processes new or changed files.

## How it works
files -> text per page/slide (PDFBox / POI) -> 120-word chunks (30 overlap) -> MiniLM embeddings (local ONNX)
-> SQLite -> cosine similarity on query -> each file scored by its best-matching chunk, as a percentage.

## Files
- `TextExtractor` text from each format, with page numbers
- `Chunker` overlapping word windows
- `Embedder` local model wrapper, unit-length vectors
- `Store` SQLite (files + chunks + vectors)
- `Indexer` folder walk, change detection, deletes stale entries
- `Searcher` in-memory brute-force cosine search
- `SearchApp` / `Main` Swing UI and CLI

## Next steps
Hybrid keyword search (SQLite FTS5), OCR for scanned PDFs, open PDF at page, folder watcher, global hotkey.
License: choose MIT (all dependencies are Apache 2.0 / BSD compatible).
