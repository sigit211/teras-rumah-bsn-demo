package core

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

import com.kms.katalon.core.util.KeywordUtil

/**
 * DbConnection
 *
 * Generic JDBC utility untuk SQL Server.
 * Semua method menerima koneksi secara eksplisit via parameter — tidak ada
 * hardcode ke database manapun. Untuk penggunaan yang sudah dikonfigurasi,
 * gunakan class turunannya seperti {@code project.db.ProjectDb}.
 *
 * Pola penggunaan:
 * <pre>
 *   String url  = DbConnection.buildUrl("host", 1433, "myDb")
 *   List rows   = DbConnection.queryForMaps(url, "user", "pass", "SELECT * FROM Tbl")
 *   int affected = DbConnection.executeUpdate(url, "user", "pass", "UPDATE Tbl SET x=? WHERE id=?", [val, id])
 * </pre>
 *
 * Semua execute-method membuka dan menutup koneksinya sendiri.
 * Gunakan {@link #openConnection} hanya jika perlu kontrol lifecycle manual.
 */
final class DbConnection {

    private static final String DRIVER = 'com.microsoft.sqlserver.jdbc.SQLServerDriver'

    private DbConnection() {}

    // ── Connection factory ────────────────────────────────────────────────────

    /**
     * Membangun JDBC URL untuk SQL Server.
     * @param host      Hostname atau IP address SQL Server.
     * @param port      Port SQL Server (default 1433).
     * @param dbName    Nama database.
     * @return String JDBC URL yang siap dipakai.
     */
    static String buildUrl(String host, int port, String dbName) {
        return "jdbc:sqlserver://${host}:${port};databaseName=${dbName};encrypt=true;trustServerCertificate=true"
    }

    /**
     * Membuka koneksi ke SQL Server.
     * Caller bertanggung jawab menutup koneksi ini (gunakan {@code conn.close()} atau try-finally).
     * Lebih direkomendasikan menggunakan execute-method yang sudah mengelola lifecycle-nya sendiri.
     * @param url       JDBC URL (bisa dibuat dengan {@link #buildUrl}).
     * @param username  Username database.
     * @param password  Password database.
     * @return Objek {@link Connection} yang sudah terhubung.
     * @throws RuntimeException jika koneksi gagal.
     */
    static Connection openConnection(String url, String username, String password) {
        KeywordUtil.logInfo("[DbConnection.openConnection] Membuka koneksi ke: ${sanitizeUrl(url)}")
        try {
            Class.forName(DRIVER)
            Connection conn = DriverManager.getConnection(url, username, password)
            KeywordUtil.logInfo("[DbConnection.openConnection] Koneksi berhasil dibuka")
            return conn
        } catch (Exception e) {
            KeywordUtil.logInfo("[DbConnection.openConnection] ERROR: ${e.message}")
            throw new RuntimeException("[DbConnection] Gagal membuka koneksi ke database: ${e.message}", e)
        }
    }

    // ── SELECT — returns List<Map> ────────────────────────────────────────────

    /**
     * Menjalankan query SELECT dan mengembalikan hasil sebagai List of Map.
     * Setiap Map merepresentasikan satu baris dengan key = nama kolom.
     * @param url     JDBC URL.
     * @param user    Username database.
     * @param pass    Password database.
     * @param sql     Query SQL SELECT.
     * @param params  Parameter untuk PreparedStatement (kosongkan atau null jika tidak ada).
     * @return List berisi Map {@code [namaKolom: nilai]} untuk setiap baris hasil.
     */
    static List<Map<String, Object>> queryForMaps(String url, String user, String pass,
                                                   String sql, List<Object> params = []) {
        KeywordUtil.logInfo("[DbConnection.queryForMaps] SQL: ${sql}")
        return withConnection(url, user, pass) { Connection conn ->
            withStatement(conn, sql, params) { ResultSet rs ->
                return rsToMaps(rs)
            }
        }
    }

    // ── SELECT — returns List<String[]> ──────────────────────────────────────

    /**
     * Menjalankan query SELECT dan mengembalikan hasil sebagai List of String array.
     * Setiap String[] merepresentasikan satu baris; urutan kolom mengikuti query.
     * @param url     JDBC URL.
     * @param user    Username database.
     * @param pass    Password database.
     * @param sql     Query SQL SELECT.
     * @param params  Parameter untuk PreparedStatement (kosongkan atau null jika tidak ada).
     * @return List berisi String[] untuk setiap baris hasil.
     */
    static List<String[]> queryForRows(String url, String user, String pass,
                                        String sql, List<Object> params = []) {
        KeywordUtil.logInfo("[DbConnection.queryForRows] SQL: ${sql}")
        return withConnection(url, user, pass) { Connection conn ->
            withStatement(conn, sql, params) { ResultSet rs ->
                return rsToRows(rs)
            }
        }
    }

    // ── SELECT — returns first row as List<String> ────────────────────────────

    /**
     * Menjalankan query SELECT dan mengembalikan baris pertama sebagai List of String.
     * Berguna untuk query yang diharapkan menghasilkan tepat satu baris.
     * @param url     JDBC URL.
     * @param user    Username database.
     * @param pass    Password database.
     * @param sql     Query SQL SELECT.
     * @param params  Parameter untuk PreparedStatement (kosongkan atau null jika tidak ada).
     * @return List String berisi nilai kolom baris pertama, atau list kosong jika tidak ada hasil.
     */
    static List<String> queryForSingleRow(String url, String user, String pass,
                                           String sql, List<Object> params = []) {
        KeywordUtil.logInfo("[DbConnection.queryForSingleRow] SQL: ${sql}")
        return withConnection(url, user, pass) { Connection conn ->
            withStatement(conn, sql, params) { ResultSet rs ->
                if (!rs.next()) return []
                int colCount = rs.metaData.columnCount
                return (1..colCount).collect { i -> rs.getObject(i)?.toString() ?: '' }
            }
        }
    }

    // ── SELECT — returns scalar (single value) ────────────────────────────────

    /**
     * Menjalankan query SELECT dan mengembalikan nilai tunggal (kolom pertama, baris pertama).
     * Cocok untuk query agregasi seperti {@code SELECT COUNT(*) FROM ...} atau {@code SELECT MAX(id) FROM ...}.
     * @param url     JDBC URL.
     * @param user    Username database.
     * @param pass    Password database.
     * @param sql     Query SQL SELECT.
     * @param params  Parameter untuk PreparedStatement (kosongkan atau null jika tidak ada).
     * @return String nilai kolom pertama baris pertama, atau null jika tidak ada hasil.
     */
    static String queryForScalar(String url, String user, String pass,
                                  String sql, List<Object> params = []) {
        KeywordUtil.logInfo("[DbConnection.queryForScalar] SQL: ${sql}")
        return withConnection(url, user, pass) { Connection conn ->
            withStatement(conn, sql, params) { ResultSet rs ->
                return rs.next() ? rs.getObject(1)?.toString() : null
            }
        }
    }

    // ── UPDATE / INSERT / DELETE ──────────────────────────────────────────────

    /**
     * Menjalankan query INSERT, UPDATE, atau DELETE.
     * @param url     JDBC URL.
     * @param user    Username database.
     * @param pass    Password database.
     * @param sql     Query SQL DML (INSERT / UPDATE / DELETE).
     * @param params  Parameter untuk PreparedStatement (kosongkan atau null jika tidak ada).
     * @return Jumlah baris yang terpengaruh.
     * @throws RuntimeException jika eksekusi gagal.
     */
    static int executeUpdate(String url, String user, String pass,
                              String sql, List<Object> params = []) {
        KeywordUtil.logInfo("[DbConnection.executeUpdate] SQL: ${sql}")
        return withConnection(url, user, pass) { Connection conn ->
            def stmt = buildStatement(conn, sql, params)
            try {
                int affected = stmt.executeUpdate()
                KeywordUtil.logInfo("[DbConnection.executeUpdate] ${affected} baris terpengaruh")
                return affected
            } catch (Exception e) {
                KeywordUtil.logInfo("[DbConnection.executeUpdate] ERROR: ${e.message}")
                throw new RuntimeException("[DbConnection] Gagal executeUpdate: ${e.message}", e)
            } finally {
                stmt?.close()
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Membuka koneksi, menjalankan action, lalu menutup koneksi secara otomatis.
     */
    private static <T> T withConnection(String url, String user, String pass, Closure<T> action) {
        Connection conn = openConnection(url, user, pass)
        try {
            return action(conn)
        } finally {
            conn?.close()
        }
    }

    /**
     * Membuat statement (PreparedStatement jika ada params, Statement jika tidak),
     * menjalankan SELECT, meneruskan ResultSet ke mapper, lalu menutup resource.
     */
    private static <T> T withStatement(Connection conn, String sql, List<Object> params, Closure<T> mapper) {
        def stmt = buildStatement(conn, sql, params)
        ResultSet rs = null
        try {
            rs = stmt.executeQuery()
            T result = mapper(rs)
            KeywordUtil.logInfo("[DbConnection] Query selesai, hasil: ${result instanceof List ? result.size() + ' baris' : result}")
            return result
        } catch (Exception e) {
            KeywordUtil.logInfo("[DbConnection.withStatement] ERROR: ${e.message}")
            throw new RuntimeException("[DbConnection] Gagal menjalankan query: ${e.message}", e)
        } finally {
            rs?.close()
            stmt?.close()
        }
    }

    /**
     * Membuat PreparedStatement. Selalu pakai PreparedStatement agar SQL tidak perlu
     * dipass ulang saat executeQuery() — menghindari NullPointerException pada Statement biasa.
     */
    private static PreparedStatement buildStatement(Connection conn, String sql, List<Object> params) {
        PreparedStatement pstmt = conn.prepareStatement(sql)
        if (params) {
            KeywordUtil.logInfo("[DbConnection] Binding ${params.size()} parameter ke PreparedStatement")
            params.eachWithIndex { val, i -> pstmt.setObject(i + 1, val) }
        }
        return pstmt
    }

    /**
     * Mengkonversi ResultSet menjadi List of Map (namaKolom → nilai).
     */
    private static List<Map<String, Object>> rsToMaps(ResultSet rs) {
        def meta = rs.metaData
        int cols = meta.columnCount
        List<Map<String, Object>> results = []
        while (rs.next()) {
            Map<String, Object> row = [:]
            (1..cols).each { i -> row[meta.getColumnName(i)] = rs.getObject(i) }
            results << row
        }
        return results
    }

    /**
     * Mengkonversi ResultSet menjadi List of String[] (nilai per kolom sebagai String).
     */
    private static List<String[]> rsToRows(ResultSet rs) {
        int cols = rs.metaData.columnCount
        List<String[]> results = []
        while (rs.next()) {
            String[] row = new String[cols]
            (1..cols).each { i -> row[i - 1] = rs.getObject(i)?.toString() ?: '' }
            results << row
        }
        return results
    }

    /**
     * Menyembunyikan credentials dari URL untuk keperluan logging (hapus password jika ada di URL).
     */
    private static String sanitizeUrl(String url) {
        return url?.replaceAll('(?i)password=[^;]*', 'password=***') ?: ''
    }
}
