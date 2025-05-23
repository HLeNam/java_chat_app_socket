package ui.components;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;

public class FileMessageComponent extends JPanel {
    private String fileId;
    private String filePath;
    private long fileSize;
    private String status;
    private boolean isCompleted = false;

    public FileMessageComponent(String fileName, long fileSize, String fileId, String sender,
                                String status, String filePath) {
        this.fileId = fileId;
        this.fileSize = fileSize;
        this.status = status;
        this.filePath = filePath;

        if (status.contains("thành công")) {
            this.isCompleted = true;
            System.out.println("File đã tải về thành công: " + fileName);
        }

        setLayout(new BorderLayout(5, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // Icon file
        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(UIManager.getIcon("FileView.fileIcon"));

        // Thông tin file
        JLabel fileNameLabel = new JLabel(fileName);
        fileNameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        String formattedSize = formatFileSize(fileSize);
        JLabel fileSizeLabel = new JLabel(formattedSize);
        fileSizeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        JLabel senderLabel = new JLabel("Từ: " + sender);
        senderLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));

        JLabel statusLabel = new JLabel(status);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        // Panel chứa thông tin file
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        infoPanel.add(fileNameLabel);
        infoPanel.add(fileSizeLabel);
        infoPanel.add(senderLabel);
        infoPanel.add(statusLabel);

        // Button mở file nếu đã tải về
        if (filePath != null && !filePath.isEmpty() && status.contains("thành công")) {
            JButton openButton = new JButton("Mở");
            openButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            openButton.addActionListener(e -> openFile());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setOpaque(false);
            buttonPanel.add(openButton);

            add(buttonPanel, BorderLayout.EAST);
        }

        add(iconLabel, BorderLayout.WEST);
        add(infoPanel, BorderLayout.CENTER);

        setBackground(new Color(240, 240, 255));
    }

    private void openFile() {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                Desktop.getDesktop().open(file);
            } else {
                JOptionPane.showMessageDialog(this,
                        "File không tồn tại hoặc đã bị di chuyển.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Không thể mở file: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String formatFileSize(long size) {
        DecimalFormat df = new DecimalFormat("#.##");
        float sizeKb = 1024.0f;
        float sizeMb = sizeKb * sizeKb;
        float sizeGb = sizeMb * sizeKb;

        if (size < sizeKb) {
            return df.format(size) + " B";
        } else if (size < sizeMb) {
            return df.format(size / sizeKb) + " KB";
        } else if (size < sizeGb) {
            return df.format(size / sizeMb) + " MB";
        } else {
            return df.format(size / sizeGb) + " GB";
        }
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;

        // Đánh dấu là đã hoàn tất nếu thành công
        if (newStatus.contains("thành công") || newStatus.contains("Đã tải xong")) {
            this.isCompleted = true;
        }

        // Cập nhật label hiển thị trạng thái
        Component[] components = ((JPanel)getComponent(1)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel)comp;
                if (label.getText().startsWith("Đang") ||
                        label.getText().contains("thành công") ||
                        label.getText().contains("từ chối") ||
                        label.getText().contains("chờ")) {
                    label.setText(newStatus);
                    break;
                }
            }
        }

        // Thêm button mở file nếu đã tải về thành công
        if (filePath != null && !filePath.isEmpty() && (newStatus.contains("thành công") || newStatus.contains("Đã tải xong"))) {
            if (getComponentCount() < 3) {  // Chưa có button
                JButton openButton = new JButton("Mở");
                openButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                openButton.addActionListener(e -> openFile());

                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttonPanel.setOpaque(false);
                buttonPanel.add(openButton);

                add(buttonPanel, BorderLayout.EAST);
                revalidate();
                repaint();
            }
        }
    }

    // Thêm phương thức này vào FileMessageComponent.java
    public void setFilePath(String filePath) {
        this.filePath = filePath;
        System.out.println("filePath updated to: " + filePath + " for fileId: " + fileId);

        // Nếu là file đã tải xong, thêm button
        if ((status.contains("thành công") || status.contains("Đã tải xong")) && filePath != null && !filePath.isEmpty()) {
            if (getComponentCount() < 3) {  // Chưa có button
                addOpenButton();
            }
        }
    }

    // Tách phần tạo button thành phương thức riêng
    private void addOpenButton() {
        System.out.println("Thêm button mở file cho fileId: " + fileId);
        JButton openButton = new JButton("Mở");
        openButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        openButton.addActionListener(e -> openFile());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        buttonPanel.add(openButton);

        add(buttonPanel, BorderLayout.EAST);
        revalidate();
        repaint();
    }

    // Kiểm tra file đã hoàn tất chưa
    public boolean isCompleted() {
        return isCompleted;
    }

    // Lấy ID của file
    public String getFileId() {
        return fileId;
    }
}