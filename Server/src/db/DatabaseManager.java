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
                "content TEXT NOT NULL, " +
                "type TEXT NOT NULL, " + // 'private', 'group', 'global'
                "timestamp INTEGER NOT NULL" +
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

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createGroupsTable);
            stmt.execute(createGroupMembersTable);
            System.out.println("Các bảng cơ bản đã được tạo (nếu chưa tồn tại)");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}
