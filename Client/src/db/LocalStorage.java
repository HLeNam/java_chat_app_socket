package db;

import model.FileInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LocalStorage {
    private Connection conn;
    private String username;

    public LocalStorage(String username) {
        this.username = username;
        initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");

            String dbName = "chat_local_" + username.replaceAll("[^a-zA-Z0-9]", "_") + ".db";
            String url = "jdbc:sqlite:database/" + dbName;

            conn = DriverManager.getConnection(url);
            if (conn != null) {
                createTables();
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("Kết nối database thành công! Driver: " +
                        meta.getDriverName());
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi kết nối đến local database: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC Driver không tìm thấy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        // Tạo bảng lưu trữ cache tin nhắn
        String createChatHistoryTable = "CREATE TABLE IF NOT EXISTS chat_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT NOT NULL, " +
                "receiver TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "type TEXT NOT NULL, " + // 'private', 'group', 'global'
                "timestamp INTEGER NOT NULL" +
                ")";

        // Tạo bảng lưu trữ thông tin file đã gửi
        String createFilesSentTable = "CREATE TABLE IF NOT EXISTS files_sent (" +
                "id TEXT PRIMARY KEY, " +
                "receiver TEXT NOT NULL, " +
                "file_name TEXT NOT NULL, " +
                "file_size INTEGER NOT NULL, " +
                "storage_path TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL" +
                ")";

        // Tạo bảng lưu trữ thông tin file đã nhận
        String createFilesReceivedTable = "CREATE TABLE IF NOT EXISTS files_received (" +
                "id TEXT PRIMARY KEY, " +
                "sender TEXT NOT NULL, " +
                "file_name TEXT NOT NULL, " +
                "file_size INTEGER NOT NULL, " +
                "storage_path TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL" +
                ")";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createChatHistoryTable);
            stmt.execute(createFilesSentTable);
            stmt.execute(createFilesReceivedTable);
        }
    }

    public boolean saveFileSent(FileInfo fileInfo) {
        String sql = "INSERT INTO files_sent(id, receiver, file_name, file_size, storage_path, timestamp) " +
                "VALUES(?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileInfo.getId());
            pstmt.setString(2, fileInfo.getReceiver());
            pstmt.setString(3, fileInfo.getFileName());
            pstmt.setLong(4, fileInfo.getFileSize());
            pstmt.setString(5, fileInfo.getStoragePath());
            pstmt.setLong(6, fileInfo.getTimestamp() != null ?
                    fileInfo.getTimestamp().getTime() : new java.util.Date().getTime());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu thông tin file đã gửi: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Lưu thông tin file đã nhận
    public boolean saveFileReceived(FileInfo fileInfo) {
        String sql = "INSERT INTO files_received(id, sender, file_name, file_size, storage_path, timestamp) " +
                "VALUES(?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileInfo.getId());
            pstmt.setString(2, fileInfo.getSender());
            pstmt.setString(3, fileInfo.getFileName());
            pstmt.setLong(4, fileInfo.getFileSize());
            pstmt.setString(5, fileInfo.getStoragePath());
            pstmt.setLong(6, fileInfo.getTimestamp() != null ?
                    fileInfo.getTimestamp().getTime() : new java.util.Date().getTime());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu thông tin file đã nhận: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Lấy danh sách file đã gửi
    public List<FileInfo> getFilesSent() {
        String sql = "SELECT * FROM files_sent ORDER BY timestamp DESC";
        List<FileInfo> filesSent = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(rs.getString("id"));
                fileInfo.setSender(username);
                fileInfo.setReceiver(rs.getString("receiver"));
                fileInfo.setFileName(rs.getString("file_name"));
                fileInfo.setFileSize(rs.getLong("file_size"));
                fileInfo.setStoragePath(rs.getString("storage_path"));
                fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                filesSent.add(fileInfo);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách file đã gửi: " + e.getMessage());
            e.printStackTrace();
        }

        return filesSent;
    }

    // Lấy danh sách file đã nhận
    public List<FileInfo> getFilesReceived() {
        String sql = "SELECT * FROM files_received ORDER BY timestamp DESC";
        List<FileInfo> filesReceived = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(rs.getString("id"));
                fileInfo.setSender(rs.getString("sender"));
                fileInfo.setReceiver(username);
                fileInfo.setFileName(rs.getString("file_name"));
                fileInfo.setFileSize(rs.getLong("file_size"));
                fileInfo.setStoragePath(rs.getString("storage_path"));
                fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                filesReceived.add(fileInfo);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách file đã nhận: " + e.getMessage());
            e.printStackTrace();
        }

        return filesReceived;
    }

    // Lấy thông tin file đã gửi theo ID
    public FileInfo getSentFileById(String fileId) {
        String sql = "SELECT * FROM files_sent WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setId(rs.getString("id"));
                    fileInfo.setSender(username);
                    fileInfo.setReceiver(rs.getString("receiver"));
                    fileInfo.setFileName(rs.getString("file_name"));
                    fileInfo.setFileSize(rs.getLong("file_size"));
                    fileInfo.setStoragePath(rs.getString("storage_path"));
                    fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                    return fileInfo;
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin file đã gửi: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Lấy thông tin file đã nhận theo ID
    public FileInfo getReceivedFileById(String fileId) {
        String sql = "SELECT * FROM files_received WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setId(rs.getString("id"));
                    fileInfo.setSender(rs.getString("sender"));
                    fileInfo.setReceiver(username);
                    fileInfo.setFileName(rs.getString("file_name"));
                    fileInfo.setFileSize(rs.getLong("file_size"));
                    fileInfo.setStoragePath(rs.getString("storage_path"));
                    fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                    return fileInfo;
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin file đã nhận: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Đóng kết nối
    public void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
                System.out.println("Local database đã đóng kết nối!");
            } catch (SQLException e) {
                System.err.println("Lỗi khi đóng kết nối đến local database: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}