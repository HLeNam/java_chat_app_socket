package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MessageDAO {
    public static boolean saveMessage(String sender, String receiver, String content, String type) {
        String sql = "INSERT INTO messages(sender, receiver, content, type, timestamp) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            pstmt.setString(4, type);
            pstmt.setLong(5, System.currentTimeMillis());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static List<Map<String, Object>> getPrivateMessages(String user1, String user2, int limit) {
        String sql = "SELECT * FROM messages WHERE " +
                "((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                "AND type = 'private' " +
                "ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.setInt(5, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", rs.getInt("id"));
                    message.put("sender", rs.getString("sender"));
                    message.put("receiver", rs.getString("receiver"));
                    message.put("content", rs.getString("content"));
                    message.put("timestamp", rs.getLong("timestamp"));
                    messages.add(message);
                }
            }

            Collections.reverse(messages);
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tin nhắn riêng tư: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public static List<Map<String, Object>> getGroupMessages(String groupName, int limit) {
        String sql = "SELECT * FROM messages WHERE receiver = ? AND type = 'group' " +
                "ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", rs.getInt("id"));
                    message.put("sender", rs.getString("sender"));
                    message.put("receiver", rs.getString("receiver"));
                    message.put("content", rs.getString("content"));
                    message.put("timestamp", rs.getLong("timestamp"));
                    messages.add(message);
                }
            }

            Collections.reverse(messages);

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tin nhắn nhóm: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public static List<Map<String, Object>> getGlobalMessages(int limit) {
        String sql = "SELECT * FROM messages WHERE type = 'global' " +
                "ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", rs.getInt("id"));
                    message.put("sender", rs.getString("sender"));
                    message.put("content", rs.getString("content"));
                    message.put("timestamp", rs.getLong("timestamp"));
                    messages.add(message);
                }
            }

            Collections.reverse(messages);

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tin nhắn toàn cục: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public static boolean deletePrivateHistory(String user1, String user2) {
        String sql = "DELETE FROM messages WHERE " +
                "((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                "AND type = 'private'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa lịch sử trò chuyện: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static List<Map<String, Object>> getOlderPrivateMessages(String user1, String user2, long olderThan, int limit) {
        String sql = "SELECT * FROM messages WHERE " +
                "((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                "AND type = 'private' AND timestamp < ? " +
                "ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.setLong(5, olderThan);
            pstmt.setInt(6, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", rs.getInt("id"));
                    message.put("sender", rs.getString("sender"));
                    message.put("receiver", rs.getString("receiver"));
                    message.put("content", rs.getString("content"));
                    message.put("timestamp", rs.getLong("timestamp"));
                    messages.add(message);
                }
            }

            Collections.reverse(messages); // Để hiển thị theo thứ tự thời gian tăng dần

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tin nhắn riêng tư cũ hơn: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public static List<Map<String, Object>> getOlderGroupMessages(String groupName, long olderThan, int limit) {
        String sql = "SELECT * FROM messages WHERE receiver = ? AND type = 'group' " +
                "AND timestamp < ? ORDER BY timestamp DESC LIMIT ?";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);
            pstmt.setLong(2, olderThan);
            pstmt.setInt(3, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", rs.getInt("id"));
                    message.put("sender", rs.getString("sender"));
                    message.put("receiver", rs.getString("receiver"));
                    message.put("content", rs.getString("content"));
                    message.put("timestamp", rs.getLong("timestamp"));
                    messages.add(message);
                }
            }

            Collections.reverse(messages);

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tin nhắn nhóm cũ hơn: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }
}
