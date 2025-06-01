package db;

import java.sql.*;
import java.util.*;

public class GroupDAO {

    // Tạo nhóm mới
    public static boolean createGroup(String groupName, String creator) {
        String sql = "INSERT INTO groups(name, creator, created_at) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);
            pstmt.setString(2, creator);
            pstmt.setLong(3, System.currentTimeMillis());

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                // Thêm người tạo nhóm vào danh sách thành viên
                return addMemberToGroup(groupName, creator);
            }

            return false;

        } catch (SQLException e) {
            System.err.println("Lỗi khi tạo nhóm: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Kiểm tra xem nhóm có tồn tại không
    public static boolean groupExists(String groupName) {
        String sql = "SELECT COUNT(*) FROM groups WHERE name = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra nhóm: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    // Thêm thành viên vào nhóm
    public static boolean addMemberToGroup(String groupName, String username) {
        String sql = "INSERT OR IGNORE INTO group_members(group_name, username, joined_at) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);
            pstmt.setString(2, username);
            pstmt.setLong(3, System.currentTimeMillis());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi thêm thành viên vào nhóm: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Xóa thành viên khỏi nhóm
    public static boolean removeMemberFromGroup(String groupName, String username) {
        String sql = "DELETE FROM group_members WHERE group_name = ? AND username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);
            pstmt.setString(2, username);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa thành viên khỏi nhóm: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Kiểm tra xem user có phải là thành viên của nhóm không
    public static boolean isGroupMember(String groupName, String username) {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_name = ? AND username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);
            pstmt.setString(2, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra thành viên nhóm: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    // Lấy danh sách thành viên của nhóm
    public static List<String> getGroupMembers(String groupName) {
        String sql = "SELECT username FROM group_members WHERE group_name = ? ORDER BY joined_at";

        List<String> members = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getString("username"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách thành viên nhóm: " + e.getMessage());
            e.printStackTrace();
        }

        return members;
    }

    // Lấy danh sách nhóm mà user tham gia
    public static List<Map<String, Object>> getUserGroups(String username) {
        String sql = "SELECT g.name, g.creator, g.created_at FROM groups g " +
                "INNER JOIN group_members gm ON g.name = gm.group_name " +
                "WHERE gm.username = ? " +
                "ORDER BY g.created_at DESC";

        List<Map<String, Object>> groups = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> group = new HashMap<>();
                    group.put("name", rs.getString("name"));
                    group.put("creator", rs.getString("creator"));
                    group.put("created_at", rs.getLong("created_at"));
                    groups.add(group);
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách nhóm của user: " + e.getMessage());
            e.printStackTrace();
        }

        return groups;
    }

    // Lấy thông tin nhóm
    public static Map<String, Object> getGroupInfo(String groupName) {
        String sql = "SELECT * FROM groups WHERE name = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("name", rs.getString("name"));
                    groupInfo.put("creator", rs.getString("creator"));
                    groupInfo.put("created_at", rs.getLong("created_at"));

                    // Thêm danh sách thành viên
                    groupInfo.put("members", getGroupMembers(groupName));

                    return groupInfo;
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin nhóm: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Xóa nhóm
    public static boolean deleteGroup(String groupName) {
        String sql1 = "DELETE FROM group_members WHERE group_name = ?";
        String sql2 = "DELETE FROM groups WHERE name = ?";

        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // Xóa thành viên trước
            try (PreparedStatement pstmt = conn.prepareStatement(sql1)) {
                pstmt.setString(1, groupName);
                pstmt.executeUpdate();
            }

            // Xóa nhóm
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setString(1, groupName);
                int rowsAffected = pstmt.executeUpdate();

                conn.commit();
                return rowsAffected > 0;
            }

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            System.err.println("Lỗi khi xóa nhóm: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}