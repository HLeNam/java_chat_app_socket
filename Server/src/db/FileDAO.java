package db;

import model.FileInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileDAO {
    public static boolean saveFileInfo(FileInfo fileInfo) {
        String sql = "INSERT INTO files (id, sender, receiver, file_name, file_size, storage_path, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileInfo.getId());
            pstmt.setString(2, fileInfo.getSender());
            pstmt.setString(3, fileInfo.getReceiver());
            pstmt.setString(4, fileInfo.getFileName());
            pstmt.setLong(5, fileInfo.getFileSize());
            pstmt.setString(6, fileInfo.getStoragePath());
            pstmt.setLong(7, fileInfo.getTimestamp().getTime());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu thông tin file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static FileInfo getFileInfoById(String fileId) {
        String sql = "SELECT * FROM files WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setId(rs.getString("id"));
                    fileInfo.setSender(rs.getString("sender"));
                    fileInfo.setReceiver(rs.getString("receiver"));
                    fileInfo.setFileName(rs.getString("file_name"));
                    fileInfo.setFileSize(rs.getLong("file_size"));
                    fileInfo.setStoragePath(rs.getString("storage_path"));
                    fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                    return fileInfo;
                }
            }

            return null;

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static List<FileInfo> getFilesSentByUser(String sender) {
        String sql = "SELECT * FROM files WHERE sender = ? ORDER BY timestamp DESC";
        List<FileInfo> files = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sender);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setId(rs.getString("id"));
                    fileInfo.setSender(rs.getString("sender"));
                    fileInfo.setReceiver(rs.getString("receiver"));
                    fileInfo.setFileName(rs.getString("file_name"));
                    fileInfo.setFileSize(rs.getLong("file_size"));
                    fileInfo.setStoragePath(rs.getString("storage_path"));
                    fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                    files.add(fileInfo);
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách file đã gửi: " + e.getMessage());
            e.printStackTrace();
        }

        return files;
    }

    // Lấy danh sách file đã nhận của một user
    public static List<FileInfo> getFilesReceivedByUser(String receiver) {
        String sql = "SELECT * FROM files WHERE receiver = ? ORDER BY timestamp DESC";
        List<FileInfo> files = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, receiver);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setId(rs.getString("id"));
                    fileInfo.setSender(rs.getString("sender"));
                    fileInfo.setReceiver(rs.getString("receiver"));
                    fileInfo.setFileName(rs.getString("file_name"));
                    fileInfo.setFileSize(rs.getLong("file_size"));
                    fileInfo.setStoragePath(rs.getString("storage_path"));
                    fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                    files.add(fileInfo);
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách file đã nhận: " + e.getMessage());
            e.printStackTrace();
        }

        return files;
    }

    // Lấy danh sách file trao đổi giữa hai user
    public static List<FileInfo> getFilesExchangedBetweenUsers(String user1, String user2) {
        String sql = "SELECT * FROM files WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
                "ORDER BY timestamp DESC";
        List<FileInfo> files = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setId(rs.getString("id"));
                    fileInfo.setSender(rs.getString("sender"));
                    fileInfo.setReceiver(rs.getString("receiver"));
                    fileInfo.setFileName(rs.getString("file_name"));
                    fileInfo.setFileSize(rs.getLong("file_size"));
                    fileInfo.setStoragePath(rs.getString("storage_path"));
                    fileInfo.setTimestamp(new Date(rs.getLong("timestamp")));
                    files.add(fileInfo);
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách file trao đổi: " + e.getMessage());
            e.printStackTrace();
        }

        return files;
    }

    // Xóa file theo ID
    public static boolean deleteFile(String fileId) {
        // Lấy thông tin file trước khi xóa
        FileInfo fileInfo = getFileInfoById(fileId);
        if (fileInfo != null) {
            // Xóa file vật lý
            java.io.File file = new java.io.File(fileInfo.getStoragePath());
            if (file.exists()) {
                file.delete();
            }
        }

        String sql = "DELETE FROM files WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, fileId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}