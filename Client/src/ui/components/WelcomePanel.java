package ui.components;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WelcomePanel  extends JPanel {
    // Kích thước tối thiểu của panel
    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 400;

    public WelcomePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(245, 245, 245));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        // Sử dụng JEditorPane để hiển thị nội dung HTML
        JEditorPane editorPane = new JEditorPane("text/html", getWelcomeHTML());
        editorPane.setEditable(false);
        editorPane.setBorder(null);
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        editorPane.setBackground(new Color(245, 245, 245));

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(null);
        scrollPane.setBackground(new Color(245, 245, 245));
        scrollPane.getViewport().setBackground(new Color(245, 245, 245));

        add(scrollPane, BorderLayout.CENTER);
    }

    private String getWelcomeHTML() {
        return "<html><body style='font-family: Arial;'>" +  // Bỏ width cố định để nội dung mở rộng theo panel
                "<div style='text-align: center;'>" +
                "<h1 style='color: #0066cc;'>Chat App</h1>" +
                "<h3>Chào mừng đến với ứng dụng chat!</h3>" +
                "</div>" +
                "<p>Đây là ứng dụng nhắn tin cho phép bạn:</p>" +
                "<ul>" +
                "<li>Trò chuyện riêng tư với người dùng khác</li>" +
                "<li>Tham gia hoặc tạo các nhóm chat</li>" +
                "<li>Gửi và nhận file</li>" +
                "</ul>" +
                "<div style='background-color: #f0f0f0; padding: 10px; border-radius: 5px;'>" +
                "<p><b>Hướng dẫn sử dụng:</b></p>" +
                "<ol>" +
                "<li>Chat riêng tư: Nhấp đúp vào tên người dùng trong danh sách 'Online'</li>" +
                "<li>Tạo nhóm: Nhấn nút 'Tạo nhóm' trong tab 'Nhóm'</li>" +
                "<li>Tham gia chat nhóm: Nhấp đúp vào tên nhóm trong danh sách</li>" +
                "<li>Gửi file: Chọn người nhận và nhấn nút 'Gửi File'</li>" +
                "<li>Xem danh sách thành viên nhóm: Nhấp chuột phải vào tab nhóm</li>" +
                "<li>Xóa tin nhắn: Nhấp chuột phải vào tin nhắn của bạn</li>" +
                "</ol>" +
                "</div>" +
                "<p style='text-align: center; margin-top: 30px; color: #666;'>" +
                "Phiên bản 1.0.0 | Phát triển bởi HLeNam<br>" +
                "Thời gian hiện tại: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()) +
                "</p>" +
                "</body></html>";
    }

    @Override
    public Dimension getMinimumSize() {
        // Đảm bảo kích thước tối thiểu
        return new Dimension(MIN_WIDTH, MIN_HEIGHT);
    }

    @Override
    public Dimension getPreferredSize() {
        // Ưu tiên kích thước lớn hơn kích thước tối thiểu một chút
        Dimension parentSize = getParent() != null ? getParent().getSize() : new Dimension(800, 600);
        int width = Math.max(MIN_WIDTH, (int)(parentSize.width * 0.7));
        int height = Math.max(MIN_HEIGHT, (int)(parentSize.height * 0.7));
        return new Dimension(width, height);
    }
}
