package ui.components;

import client.ChatClient;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;

public class FileMessageComponent extends JPanel {
    private String fileId;
    private String filePath;
    private long fileSize;
    private String fileName;
    private String sender;
    private String status;
    private boolean isCompleted = false;
    private boolean fileExists;
    private ChatClient client;

    public FileMessageComponent(String fileName, long fileSize, String fileId, String sender,
                                String status, String filePath, ChatClient client) {
        this.fileId = fileId;
        this.fileSize = fileSize;
        this.status = status;
        this.filePath = filePath;
        this.client = client;
        this.fileName = fileName;
        this.sender = sender;

        // Kiểm tra xem file đã tồn tại hay chưa
        this.fileExists = false;
        if (filePath != null && !filePath.isEmpty()) {
            File file = new File(filePath);
            this.fileExists = file.exists();
        }

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

//        updateFileButton();
    }

    private void openFile() {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                Desktop.getDesktop().open(file);
            } else {
                // Nếu file không tồn tại, hỏi người dùng có muốn tải lại không
                int option = JOptionPane.showConfirmDialog(this,
                        "File không tồn tại hoặc đã bị di chuyển. Bạn có muốn tải lại từ server không?",
                        "File không tồn tại", JOptionPane.YES_NO_OPTION);

                if (option == JOptionPane.YES_OPTION) {
                    downloadFileFromServer();
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Không thể mở file: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
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
        // Tìm kiếm các JLabel trong infoPanel
        for (Component comp : getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp; // Lấy panel
                Component[] panelComponents = panel.getComponents();
                for (Component labelComp : panelComponents) {
                    if (labelComp instanceof JLabel) {
                        JLabel label = (JLabel) labelComp;
                        if (label.getText().startsWith("Đang") ||
                                label.getText().contains("thành công") ||
                                label.getText().contains("từ chối") ||
                                label.getText().contains("chờ")) {
                            label.setText(newStatus);
                            break;
                        }
                    }
                }
            }
        }

        // Thêm button mở file nếu đã tải về thành công
        if (filePath != null && !filePath.isEmpty() && (newStatus.contains("thành công") || newStatus.contains("Đã tải xong"))) {
            if (getComponentCount() < 3) {  // Chưa có button
                addOpenButton();
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

    private void updateFileButton() {
        // Xóa button cũ nếu có
        for (Component comp : getComponents()) {
            if (comp instanceof JPanel && ((JPanel) comp).getLayout() instanceof FlowLayout) {
                remove(comp);
            }
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);

        if (fileExists && (status.contains("thành công") || status.contains("Đã tải xong"))) {
            // File tồn tại - hiển thị nút mở
            JButton openButton = new JButton("Mở");
            openButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            openButton.addActionListener(e -> openFile());
            buttonPanel.add(openButton);
        } else if (!fileExists && filePath != null && !filePath.isEmpty()) {
            // File không tồn tại nhưng có đường dẫn - hiển thị nút tải
            JButton downloadButton = new JButton("Tải xuống");
            downloadButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            downloadButton.addActionListener(e -> downloadFileFromServer());
            buttonPanel.add(downloadButton);
        }

        if (!buttonPanel.getComponents().equals(0)) {
            add(buttonPanel, BorderLayout.EAST);
        }

        revalidate();
        repaint();
    }

    private void downloadFileFromServer() {
        if (client != null) {
            String savePath = ChatClient.defaultDownloadFolder + File.separator + fileName;

            // Cập nhật UI để hiển thị đang tải
            updateStatus("Đang tải từ server...");

            // Gọi phương thức tải file từ server
            client.downloadFileFromServer(fileId, sender, fileName, fileSize);
        }
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