package db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    public static boolean validateLogin(String username, String password) {
        String sql = "SELECT password FROM users WHERE username= ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    return storedPassword.equals(password);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra đăng nhập: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public static boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra username: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public static boolean registerUser(String username, String password, String fullName, String email) {
        String sql = "INSERT INTO users(username, password, full_name, email, created_at) VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, fullName);
            pstmt.setString(4, email);
            pstmt.setLong(5, System.currentTimeMillis());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi đăng ký người dùng: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static Object[] getUserInfo(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Object[]{
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getLong("created_at")
                    };
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin người dùng: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public static List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(rs.getString("username"));
            }

        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách người dùng: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    public static boolean updateUserInfo(String username, String fullName, String email) {
        String sql = "UPDATE users SET full_name = ?, email = ? WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, fullName);
            pstmt.setString(2, email);
            pstmt.setString(3, username);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật thông tin người dùng: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!validateLogin(username, oldPassword)) {
            return false;
        }

        String sql = "UPDATE users SET password = ? WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPassword);
            pstmt.setString(2, username);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi thay đổi mật khẩu: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
