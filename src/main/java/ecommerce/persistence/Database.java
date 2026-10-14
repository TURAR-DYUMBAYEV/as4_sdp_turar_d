package ecommerce.persistence;

import ecommerce.exception.PersistenceException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Short transactions belong to one stage only; no transaction spans A, B and C. */
public final class Database {
    private final String url;
    public Database(Path path) {
        url = "jdbc:sqlite:" + path.toAbsolutePath();
        transaction(c -> {
            execute(c, "CREATE TABLE IF NOT EXISTS inventory(product TEXT PRIMARY KEY, stock INTEGER NOT NULL CHECK(stock>=0))");
            execute(c, "CREATE TABLE IF NOT EXISTS reservations(id TEXT PRIMARY KEY, product TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>0), status TEXT NOT NULL)");
            execute(c, "CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY, product TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>0), status TEXT NOT NULL)");
            execute(c, "CREATE TABLE IF NOT EXISTS shipments(id TEXT PRIMARY KEY, status TEXT NOT NULL, payment TEXT NOT NULL, tracking TEXT NOT NULL)");
            execute(c, "CREATE TABLE IF NOT EXISTS workflows(id TEXT PRIMARY KEY, product TEXT NOT NULL, quantity INTEGER NOT NULL, mode TEXT NOT NULL, status TEXT NOT NULL, error TEXT NOT NULL DEFAULT '')");
            execute(c, "CREATE TABLE IF NOT EXISTS history(seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, event TEXT NOT NULL, created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            execute(c, "INSERT OR IGNORE INTO inventory VALUES('BOOK',100),('LAPTOP',20),('MOUSE',50)");
            return null;
        });
    }
    @FunctionalInterface public interface Work<T> { T run(Connection c) throws SQLException; }
    public <T> T transaction(Work<T> work) {
        try (Connection c = DriverManager.getConnection(url)) {
            try (Statement s = c.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); }
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) { throw new PersistenceException("SQLite operation failed: " + e.getMessage(), e); }
    }
    public static int execute(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p,args);
            return p.executeUpdate();
        }
    }
    public static List<Map<String,String>> query(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p,args);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String,String>> rows = new ArrayList<>();
                while (r.next()) {
                    Map<String,String> row = new LinkedHashMap<>();
                    for (int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i),r.getString(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    private static void bind(PreparedStatement p,Object[] args) throws SQLException {
        for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]);
    }
}
