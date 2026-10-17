package semfind;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** SQLite storage: files (for change detection) and chunks (text + vector). */
public final class Store implements AutoCloseable {
    public record Chunk(String path, int page, String text, float[] vec) {}

    private final Connection db;

    public Store(Path dbFile) throws SQLException, IOException {
        Files.createDirectories(dbFile.toAbsolutePath().getParent());
        db = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("CREATE TABLE IF NOT EXISTS files(path TEXT PRIMARY KEY, modified INTEGER, size INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS chunks(id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "path TEXT NOT NULL REFERENCES files(path) ON DELETE CASCADE, "
                    + "page INTEGER, text TEXT, vec BLOB)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_chunks_path ON chunks(path)");
            s.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)");
        }
    }

    /** Vectors from different models can't be mixed, so a model change wipes the index. */
    public void ensureModel(String name) throws SQLException {
        String current = null;
        try (Statement s = db.createStatement();
             ResultSet rs = s.executeQuery("SELECT v FROM meta WHERE k='model'")) {
            if (rs.next()) current = rs.getString(1);
        }
        if (!name.equals(current)) {
            try (Statement s = db.createStatement()) {
                s.execute("DELETE FROM files");
            }
            try (PreparedStatement ps = db.prepareStatement("INSERT OR REPLACE INTO meta(k,v) VALUES('model',?)")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        }
    }

    public boolean isCurrent(String path, long modified, long size) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT 1 FROM files WHERE path=? AND modified=? AND size=?")) {
            ps.setString(1, path);
            ps.setLong(2, modified);
            ps.setLong(3, size);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void replaceFile(String path, long modified, long size, List<Chunk> chunks) throws SQLException {
        db.setAutoCommit(false);
        try {
            delete(path);
            try (PreparedStatement f = db.prepareStatement("INSERT INTO files(path,modified,size) VALUES(?,?,?)")) {
                f.setString(1, path);
                f.setLong(2, modified);
                f.setLong(3, size);
                f.executeUpdate();
            }
            try (PreparedStatement c = db.prepareStatement(
                    "INSERT INTO chunks(path,page,text,vec) VALUES(?,?,?,?)")) {
                for (Chunk ch : chunks) {
                    c.setString(1, path);
                    c.setInt(2, ch.page());
                    c.setString(3, ch.text());
                    c.setBytes(4, toBytes(ch.vec()));
                    c.addBatch();
                }
                c.executeBatch();
            }
            db.commit();
        } catch (SQLException | RuntimeException e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    public void delete(String path) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("DELETE FROM files WHERE path=?")) {
            ps.setString(1, path);
            ps.executeUpdate();
        }
    }

    public List<String> allPaths() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet rs = s.executeQuery("SELECT path FROM files")) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    public List<Chunk> loadAll() throws SQLException {
        List<Chunk> out = new ArrayList<>();
        try (Statement s = db.createStatement();
             ResultSet rs = s.executeQuery("SELECT path,page,text,vec FROM chunks")) {
            while (rs.next()) {
                out.add(new Chunk(rs.getString(1), rs.getInt(2), rs.getString(3), toFloats(rs.getBytes(4))));
            }
        }
        return out;
    }

    private static byte[] toBytes(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) b.putFloat(f);
        return b.array();
    }

    private static float[] toFloats(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return v;
    }

    @Override
    public void close() throws SQLException {
        db.close();
    }
}
