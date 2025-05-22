package ui.components;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FileGroupComponent extends JPanel {
    private List<FileMessageComponent> fileComponents = new ArrayList<>();
    private JPanel filesContainer;
    private String sender;
    private boolean isExpanded = true;

    public FileGroupComponent(String sender, int fileCount) {
        this.sender = sender;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // Header với thông tin về nhóm file
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel headerLabel = new JLabel(sender + " đã gửi " + fileCount + " files");
        headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        headerPanel.add(headerLabel, BorderLayout.WEST);

        // Nút để mở rộng/thu gọn danh sách file
        JButton toggleButton = new JButton("▼");
        toggleButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        toggleButton.setMargin(new Insets(0, 5, 0, 5));
        toggleButton.addActionListener(e -> toggleExpand(toggleButton));
        headerPanel.add(toggleButton, BorderLayout.EAST);

        // Container cho các file components
        filesContainer = new JPanel();
        filesContainer.setLayout(new BoxLayout(filesContainer, BoxLayout.Y_AXIS));
        filesContainer.setOpaque(false);

        add(headerPanel, BorderLayout.NORTH);
        add(filesContainer, BorderLayout.CENTER);

        setBackground(new Color(240, 245, 255));
    }

    public void addFileComponent(FileMessageComponent component) {
        fileComponents.add(component);
        filesContainer.add(component);
        filesContainer.revalidate();
        filesContainer.repaint();
    }

    private void toggleExpand(JButton toggleButton) {
        isExpanded = !isExpanded;

        toggleButton.setText(isExpanded ? "▼" : "▶");
        filesContainer.setVisible(isExpanded);

        revalidate();
        repaint();
    }

    // Kiểm tra xem cần hiển thị nhóm này không (nếu tất cả file đã hoàn tất)
    public boolean areAllFilesCompleted() {
        if (fileComponents.isEmpty()) {
            return false;
        }

        for (FileMessageComponent component : fileComponents) {
            if (!component.isCompleted()) {
                return false;
            }
        }

        return true;
    }

    // Lấy số lượng file trong nhóm
    public int getFileCount() {
        return fileComponents.size();
    }

    // Lấy sender
    public String getSender() {
        return sender;
    }
}