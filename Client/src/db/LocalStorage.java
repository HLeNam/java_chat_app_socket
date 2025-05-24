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

        //
        String createMessageTable =  "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, " +
                "receiver TEXT, " +
                "message TEXT, " +
                "timestamp LONG, " +
                "is_group INTEGER DEFAULT 0, " +
                "message_type TEXT DEFAULT 'text', " +  // 'text' hoặc 'file'
                "file_name TEXT, " +
                "file_size LONG, " +
                "file_id TEXT, " +
                "file_path TEXT)";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createChatHistoryTable);
            stmt.execute(createFilesSentTable);
            stmt.execute(createFilesReceivedTable);
            stmt.execute(createMessageTable);
        }
    }

    public boolean saveTextMessage(String sender, String receiver, String content,
                                   boolean isGroup, long timestamp) {
        String sql = "INSERT INTO messages(sender, receiver, message, timestamp, " +
                "is_group, message_type) " +
                "VALUES(?, ?, ?, ?, ?, 'text')";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            pstmt.setLong(4, timestamp);
            pstmt.setInt(5, isGroup ? 1 : 0);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tin nhắn văn bản: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean saveFileMessage(String sender, String receiver, String fileName,
                                   long fileSize, String fileId, String filePath,
                                   long timestamp, boolean isGroup) {
        String sql = "INSERT INTO messages(sender, receiver, timestamp, is_group, " +
                "message_type, file_name, file_size, file_id, file_path) " +
                "VALUES(?, ?, ?, ?, 'file', ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setLong(3, timestamp);
            pstmt.setInt(4, isGroup ? 1 : 0);
            pstmt.setString(5, fileName);
            pstmt.setLong(6, fileSize);
            pstmt.setString(7, fileId);
            pstmt.setString(8, filePath);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tin nhắn file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public List<Object[]> getChatHistory(String chatPartner, boolean isGroup, int limit) {
        List<Object[]> messages = new ArrayList<>();
        String sql;

        if (isGroup) {
            sql = "SELECT sender, receiver, message, message_type, timestamp, " +
                    "file_name, file_size, file_id, file_path " +
                    "FROM messages WHERE receiver = ? AND is_group = 1 " +
                    "ORDER BY timestamp ASC LIMIT ?";
        } else {
            sql = "SELECT sender, receiver, message, message_type, timestamp, " +
                    "file_name, file_size, file_id, file_path " +
                    "FROM messages WHERE " +
                    "((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                    "AND is_group = 0 ORDER BY timestamp ASC LIMIT ?";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (isGroup) {
                pstmt.setString(1, chatPartner);
                pstmt.setInt(2, limit);
            } else {
                pstmt.setString(1, username);
                pstmt.setString(2, chatPartner);
                pstmt.setString(3, chatPartner);
                pstmt.setString(4, username);
                pstmt.setInt(5, limit);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String messageType = rs.getString("message_type");
                    Object[] messageData;

                    if ("text".equals(messageType)) {
                        messageData = new Object[5];
                        messageData[0] = rs.getString("sender");
                        messageData[1] = rs.getString("message");
                        messageData[2] = rs.getLong("timestamp");
                        messageData[3] = messageType;
                        messageData[4] = rs.getString("receiver");
                    } else { // file
                        messageData = new Object[8];
                        messageData[0] = rs.getString("sender");
                        messageData[1] = rs.getString("file_id");
                        messageData[2] = rs.getLong("timestamp");
                        messageData[3] = messageType;
                        messageData[4] = rs.getString("file_name");
                        messageData[5] = rs.getLong("file_size");
                        messageData[6] = rs.getString("file_path");
                        messageData[7] = rs.getString("receiver");
                    }

                    messages.add(messageData);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy lịch sử chat: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
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