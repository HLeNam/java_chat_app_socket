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

    public static boolean deleteFileInfoById(String fileId) {
        String sql = "DELETE FROM files WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, fileId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa thông tin file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static String getFileStoragePath(String fileId) {
        String sql = "SELECT storage_path FROM files WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("storage_path");
                }
            }

            return null;

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy đường dẫn lưu trữ file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static boolean deleteFileInStorage(String fileId) {
        String storagePath = getFileStoragePath(fileId);
        if (storagePath == null) {
            System.err.println("Không tìm thấy đường dẫn lưu trữ cho file ID: " + fileId);
            return false;
        }

        java.io.File file = new java.io.File(storagePath);
        if (file.exists()) {
            return file.delete();
        } else {
            System.err.println("File không tồn tại trong lưu trữ: " + storagePath);
            return false;
        }
    }
}