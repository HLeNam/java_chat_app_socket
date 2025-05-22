package client;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Thử sử dụng giao diện hệ thống
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Khởi tạo các thông số
        String host = "localhost";
        int port = 9999;
        int filePort = 9998;

        // Đọc tham số từ command line nếu có
        if (args.length > 0) {
            host = args[0];
            if (args.length > 1) {
                try {
                    port = Integer.parseInt(args[1]);
                    if (args.length > 2) {
                        filePort = Integer.parseInt(args[2]);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Lỗi định dạng port: " + e.getMessage());
                    System.exit(1);
                }
            }
        }

        // Khởi động client
        ChatClient client = new ChatClient();
        client.showLoginFrame();
    }
}
