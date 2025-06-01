package ui.components;

import client.ChatClient;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MessagePanel extends JPanel {
    private String messageId;
    private String sender;
    private String content;
    private long timestamp;
    private String chatContext;
    private boolean isGroup;
    private ChatClient client;
    private JTextArea contentArea;

    public MessagePanel(String messageId, String sender, String content, long timestamp,
                        String chatContext, boolean isGroup, ChatClient client) {
        this.messageId = messageId;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
        this.chatContext = chatContext;
        this.isGroup = isGroup;
        this.client = client;

        setLayout(new BorderLayout(5, 0));
        setName("message_" + messageId);
        setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        setOpaque(false);

        // Hiện thị thời gian
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        JLabel timeLabel = new JLabel("[" + sdf.format(new Date(timestamp)) + "]");
        timeLabel.setForeground(Color.GRAY);
        timeLabel.setFont(new Font(timeLabel.getFont().getName(), Font.PLAIN, 10));

        // Hiển thị người gửi
        JLabel senderLabel = new JLabel(sender + ": ");
        if (sender.equals(client.getCurrentUser().getUsername())) {
            senderLabel.setForeground(new Color(0, 128, 0)); // Màu xanh lá cho người gửi
        } else {
            senderLabel.setForeground(Color.BLUE);
        }
        senderLabel.setFont(new Font(senderLabel.getFont().getName(), Font.BOLD, 12));

        // Hiển thị nội dung tin nhắn
        contentArea = new JTextArea(content);
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setBackground(new Color(245, 245, 245));
        contentArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Tự động điều chỉnh kích thước của JTextArea
        adjustTextAreaSize();

        // Panel chứa thông tin tin nhắn
        JPanel messageInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        messageInfoPanel.setOpaque(false);
        messageInfoPanel.add(timeLabel);
        messageInfoPanel.add(senderLabel);

        add(messageInfoPanel, BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);

        addPopupMenu();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setBorder(new CompoundBorder(
                        BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                        new EmptyBorder(1, 4, 1, 4)));
                setBackground(new Color(240, 240, 240));
                setOpaque(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                setOpaque(false);
            }
        });

        // Thiết lập alignment và maxSize cho đúng
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    // Phương thức để điều chỉnh kích thước JTextArea dựa trên nội dung
    private void adjustTextAreaSize() {
        // Tính số dòng cần thiết
        FontMetrics fm = contentArea.getFontMetrics(contentArea.getFont());
        int fontHeight = fm.getHeight();

        // Giả định chiều rộng JTextArea khoảng 300 pixels
        int contentWidth = 300;

        // Tính số dòng dựa trên content
        String[] lines = content.split("\n");
        int totalLines = 0;

        for (String line : lines) {
            int lineWidth = SwingUtilities.computeStringWidth(fm, line);
            int linesNeeded = Math.max(1, (lineWidth + 10) / contentWidth);
            totalLines += linesNeeded;
        }

        totalLines = Math.max(1, totalLines); // Đảm bảo ít nhất 1 dòng

        // Đặt số dòng cho text area
        contentArea.setRows(totalLines);

        // Đặt preferred size dựa trên kích thước nội dung
        Dimension prefSize = contentArea.getPreferredSize();
        contentArea.setPreferredSize(prefSize);
    }

    private void addPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        // Chỉ hiển thị tùy chọn xóa nếu người dùng hiện tại là người gửi tin nhắn
        if (sender.equals(client.getCurrentUser().getUsername())) {
            JMenuItem deleteItem = new JMenuItem("Xóa tin nhắn");
            deleteItem.addActionListener(e -> {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        "Bạn có chắc muốn xóa tin nhắn này?",
                        "Xác nhận xóa",
                        JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    client.deleteMessage(messageId, chatContext, isGroup);
                }
            });
            popupMenu.add(deleteItem);
        }

        // Thiết lập menu popup
        setComponentPopupMenu(popupMenu);
    }

    public String getMessageId() {
        return messageId;
    }

    // Override getMaximumSize để đảm bảo panel chỉ chiếm đúng chiều cao cần thiết
    @Override
    public Dimension getMaximumSize() {
        Dimension d = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, d.height);
    }

    // Override getPreferredSize để tính lại chiều cao dựa trên nội dung
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (contentArea != null) {
            // Đảm bảo chiều cao phản ánh nội dung thực tế
            d.height = contentArea.getPreferredSize().height + 30; // thêm margin
        }
        return d;
    }
}