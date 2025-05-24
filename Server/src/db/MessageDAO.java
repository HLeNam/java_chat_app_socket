package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MessageDAO {
    public static boolean saveTextMessage(String sender, String receiver, String content, String type,
                                       boolean isGroup, long timestamp) {
        String sql = "INSERT INTO messages (sender, receiver, content, type, message_type, " +
                "is_group, timestamp) VALUES (?, ?, ?, ?, 'text', ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            pstmt.setString(4, type);
            pstmt.setBoolean(5, isGroup);
            pstmt.setLong(6, timestamp);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tin nhắn: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean saveFileMessage(String sender, String receiver, String fileId,
                                          String fileName, long fileSize, String type,
                                          boolean isGroup, long timestamp) {
        String sql = "INSERT INTO messages (sender, receiver, type, message_type, " +
                "is_group, timestamp, file_id, file_name, file_size) " +
                "VALUES (?, ?, ?, 'file', ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, type);
            pstmt.setBoolean(4, isGroup);
            pstmt.setLong(5, timestamp);
            pstmt.setString(6, fileId);
            pstmt.setString(7, fileName);
            pstmt.setLong(8, fileSize);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tin nhắn file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static List<Object[]> getChatHistory(String user1, String user2, boolean isGroup, int limit) {
        List<Object[]> messages = new ArrayList<>();
        String sql = getChatHistorySql(isGroup, false);

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (isGroup) {
                pstmt.setString(1, user2); // username for group messages
                pstmt.setString(2, user1); // group name
                pstmt.setBoolean(3, true);
                pstmt.setInt(4, limit);
            } else {
                pstmt.setString(1, user1);
                pstmt.setString(2, user2);
                pstmt.setString(3, user2);
                pstmt.setString(4, user1);
                pstmt.setBoolean(5, false);
                pstmt.setInt(6, limit);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Object[] messageData = new Object[11];
                    messageData[0] = rs.getLong("id");
                    messageData[1] = rs.getString("sender");
                    messageData[2] = rs.getString("receiver");
                    messageData[3] = rs.getString("content");
                    messageData[4] = rs.getString("message_type");
                    messageData[5] = rs.getLong("timestamp");
                    messageData[6] = rs.getString("file_id");
                    messageData[7] = rs.getString("file_name");
                    messageData[8] = rs.getLong("file_size");
                    messageData[9] = rs.getString("actual_filename_save");
                    messageData[10] = rs.getString("actual_filename_upload");

                    messages.add(messageData);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy lịch sử trò chuyện: " + e.getMessage());
            e.printStackTrace();
        }

        // Đảo ngược danh sách để hiển thị theo thứ tự thời gian tăng dần
        Collections.reverse(messages);

        return messages;
    }

    public static void saveMessageGroupActualFilename(String fileId, String username) {
        String sql = "INSERT INTO message_group_actual_filename (file_id, username) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            pstmt.setString(2, username);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Lưu tên file thành công.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tên file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateActualFilenameSaveInMessageGroupFileName(String newFileName, String fileId, String username) {
        String sql = "UPDATE message_group_actual_filename SET actual_filename_save = ? " +
                     "WHERE file_id = ? AND username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFileName);
            pstmt.setString(2, fileId);
            pstmt.setString(3, username);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Cập nhật tên file thành công.");
            } else {
                System.out.println("Không tìm thấy bản ghi để cập nhật.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật tên file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateActualFilenameUploadInMessageGroupFileName(String newFileName, String fileId, String username) {
        String sql = "UPDATE message_group_actual_filename SET actual_filename_upload = ? " +
                "WHERE file_id = ? AND username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFileName);
            pstmt.setString(2, fileId);
            pstmt.setString(3, username);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Cập nhật tên file thành công.");
            } else {
                System.out.println("Không tìm thấy bản ghi để cập nhật.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật tên file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateActualFilenameSave(String newFileName, String fileId) {
        String sql = "UPDATE messages SET actual_filename_save = ? WHERE file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFileName);
            pstmt.setString(2, fileId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Cập nhật tên file thành công.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật tên file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateActualFilenameUpload(String newFileName, String fileId) {
        String sql = "UPDATE messages SET actual_filename_upload = ? WHERE file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFileName);
            pstmt.setString(2, fileId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Cập nhật tên file thành công.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật tên file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateFileName(long messageId, String newFileName, String fileId) {
        String sql = "UPDATE messages SET file_name = ? WHERE id = ? AND file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFileName);
            pstmt.setLong(2, messageId);
            pstmt.setString(3, fileId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Cập nhật tên file thành công.");
            } else {
                System.out.println("Không tìm thấy tin nhắn với ID: " + messageId);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật tên file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<Object[]> getOlderMessages(String user1, String user2, boolean isGroup,
                                                  long olderThan, int limit) {
        List<Object[]> messages = new ArrayList<>();
        String sql = getChatHistorySql(isGroup, true);

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (isGroup) {
                pstmt.setString(1, user2); // username for group messages
                pstmt.setString(2, user1); // group name
                pstmt.setBoolean(3, true);
                pstmt.setLong(4, olderThan);
                pstmt.setInt(5, limit);
            } else {
                pstmt.setString(1, user1);
                pstmt.setString(2, user2);
                pstmt.setString(3, user2);
                pstmt.setString(4, user1);
                pstmt.setBoolean(5, false);
                pstmt.setLong(6, olderThan);
                pstmt.setInt(7, limit);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Object[] messageData = new Object[11];
                    messageData[0] = rs.getLong("id");
                    messageData[1] = rs.getString("sender");
                    messageData[2] = rs.getString("receiver");
                    messageData[3] = rs.getString("content");
                    messageData[4] = rs.getString("message_type");
                    messageData[5] = rs.getLong("timestamp");
                    messageData[6] = rs.getString("file_id");
                    messageData[7] = rs.getString("file_name");
                    messageData[8] = rs.getLong("file_size");
                    messageData[9] = rs.getString("actual_filename_save");
                    messageData[10] = rs.getString("actual_filename_upload");

                    messages.add(messageData);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tin nhắn cũ hơn: " + e.getMessage());
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

        // Đảo ngược danh sách để hiển thị theo thứ tự thời gian tăng dần
        Collections.reverse(messages);

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

    private static String getChatHistorySql(boolean isGroup, boolean withTimestamp) {
        String sql;

        if (!withTimestamp) {
            if (isGroup) {
                sql = "SELECT M.id, M.sender, M.receiver, M.content, M.message_type, M.timestamp, " +
                        "M.file_id, M.file_name, M.file_size, T.actual_filename_save, T.actual_filename_upload " +
                        "FROM messages AS M " +
                        "LEFT JOIN message_group_actual_filename AS T ON M.file_id = T.file_id " +
                        "AND T.username = ? " +
                        "WHERE M.receiver = ? AND M.is_group = ? " +
                        "ORDER BY M.timestamp DESC LIMIT ?";
            } else {
                sql = "SELECT id, sender, receiver, content, message_type, timestamp, " +
                        "file_id, file_name, file_size, actual_filename_save, actual_filename_upload FROM messages " +
                        "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
                        "AND is_group = ? " +
                        "ORDER BY timestamp DESC LIMIT ?";
            }
        } else {
            if (isGroup) {
                sql = "SELECT M.id, M.sender, M.receiver, M.content, M.message_type, M.timestamp, " +
                        "M.file_id, M.file_name, M.file_size, T.actual_filename_save, T.actual_filename_upload " +
                        "FROM messages AS M " +
                        "LEFT JOIN message_group_actual_filename AS T ON M.file_id = T.file_id " +
                        "AND T.username = ? " +
                        "WHERE M.receiver = ? AND M.is_group = ? AND M.timestamp < ? " +
                        "ORDER BY M.timestamp DESC LIMIT ?";
            } else {
                sql = "SELECT id, sender, receiver, content, message_type, timestamp, " +
                        "file_id, file_name, file_size, actual_filename_save, actual_filename_upload FROM messages " +
                        "WHERE ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                        "AND is_group = ? AND timestamp < ? " +
                        "ORDER BY timestamp DESC LIMIT ?";
            }
        }

        return sql;
    }
}
