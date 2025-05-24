package db;

import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:database/chatapp.db";
    private static boolean initialized = false;

    public static void initDatabase() {
        if (initialized) {
            return;
        }

        try {
            Class.forName("org.sqlite.JDBC");

            try (Connection conn = getConnection()) {
                if (conn != null) {
                    DatabaseMetaData meta = conn.getMetaData();
                    System.out.println("Kết nối database thành công! Driver: " +
                            meta.getDriverName());
                }

                createTablesIfNotExist(conn);
                initialized = true;
            }
        }  catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC Driver không tìm thấy: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Lỗi khởi tạo database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTablesIfNotExist(Connection conn) throws SQLException {
        // Bảng người dùng
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY, " +
                "password TEXT NOT NULL, " +
                "full_name TEXT, " +
                "email TEXT, " +
                "created_at INTEGER NOT NULL" +
                ")";

        // Bảng tin nhắn
        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT NOT NULL, " +
                "receiver TEXT NOT NULL, " +
                "content TEXT, " +
                "type TEXT NOT NULL, " +    // 'private', 'group', 'global'
                "message_type TEXT NOT NULL, " +    // 'text' hoặc 'file'
                "is_group BOOLEAN NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +     // Dùng timestamp để sắp xếp
                "file_id TEXT, " +
                "file_name TEXT, " +
                "file_size BIGINT," +
                "actual_filename_save TEXT, " + // Tên file thực tế trên được lưu trên client
                "actual_filename_upload TEXT " + // Tên file thực tế
                ")";

        String createMessageGroupActualFilenameTable = "CREATE TABLE IF NOT EXISTS message_group_actual_filename (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "file_id TEXT NOT NULL, " +
                "username TEXT NOT NULL, " +
                "actual_filename_save TEXT, " +
                "actual_filename_upload TEXT " +
                ")";

        String createGroupsTable = "CREATE TABLE IF NOT EXISTS groups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL UNIQUE, " +
                "creator TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL" +
                ")";

        String createGroupMembersTable = "CREATE TABLE IF NOT EXISTS group_members (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "group_name TEXT NOT NULL, " +
                "username TEXT NOT NULL, " +
                "joined_at INTEGER NOT NULL, " +
                "UNIQUE(group_name, username)" +
                ")";

        String createFilesTable = "CREATE TABLE IF NOT EXISTS files (" +
                "id TEXT PRIMARY KEY, " +
                "sender TEXT, " +
                "receiver TEXT, " +
                "file_name TEXT NOT NULL, " +
                "file_size BIGINT NOT NULL, " +
                "storage_path TEXT NOT NULL, " +
                "timestamp BIGINT" +
                ")";

        String del1 = "DROP TABLE IF EXISTS message_group_actual_filename";
        String del2 = "DROP TABLE IF EXISTS files";
        String del5 = "DROP TABLE IF EXISTS messages";

        try (Statement stmt = conn.createStatement()) {
            // Xóa các bảng cũ nếu cần thiết
//            stmt.execute(del1);
//            stmt.execute(del2);
//            stmt.execute(del5);
            stmt.execute(createUsersTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createGroupsTable);
            stmt.execute(createGroupMembersTable);
            stmt.execute(createMessageGroupActualFilenameTable);
            stmt.execute(createFilesTable);
            System.out.println("Các bảng cơ bản đã được tạo (nếu chưa tồn tại)");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}
