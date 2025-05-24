package ui.components;

import client.ChatClient;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileTransferPanel extends JPanel {
    private ChatClient client;
    private Map<String, JProgressBar> uploadProgressBars = new HashMap<>();
    private Map<String, JProgressBar> downloadProgressBars = new HashMap<>();
    private JPanel uploadPanel;
    private JPanel downloadPanel;

    public FileTransferPanel(ChatClient client) {
        this.client = client;

        setLayout(new GridLayout(2, 1, 0, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        uploadPanel = new JPanel();
        uploadPanel.setLayout(new BoxLayout(uploadPanel, BoxLayout.Y_AXIS));
        uploadPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Đang gửi",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.TOP));
        JScrollPane uploadScrollPane = new JScrollPane(uploadPanel);
        uploadScrollPane.setPreferredSize(new Dimension(300, 150));

        downloadPanel = new JPanel();
        downloadPanel.setLayout(new BoxLayout(downloadPanel, BoxLayout.Y_AXIS));
        downloadPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Đang nhận",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.TOP));
        JScrollPane downloadScrollPane = new JScrollPane(downloadPanel);
        downloadScrollPane.setPreferredSize(new Dimension(300, 150));

        add(uploadScrollPane);
        add(downloadScrollPane);
    }

    public void sendFile(String receiver, boolean isGroup) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            for (File file : selectedFiles) {
                if (file.length() > 100 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(this,
                            "File " + file.getName() + " quá lớn. Giới hạn 100MB mỗi file.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    continue;
                }

                // Gửi yêu cầu chuyển file đến server
                String fileId;
                if (isGroup) {
                    fileId = client.sendGroupFileRequest(receiver, file);
                } else {
                    fileId = client.sendFileRequest(receiver, file);
                }

                JProgressBar progressBar = addUploadProgressBar(file.getName(), receiver, fileId);
            }
        }
    }

    public void handleFileRequest(String fileId, String sender, String fileName, long fileSize) {
        SwingUtilities.invokeLater(() -> {
            int option = JOptionPane.showConfirmDialog(this,
                    sender + " muốn gửi cho bạn file: " + fileName + " ("
                            + formatFileSize(fileSize) + ")\nBạn có muốn nhận không?",
                    "Yêu cầu file", JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                // Chọn nơi lưu file
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(new File(fileName));
                int result = fileChooser.showSaveDialog(this);

                if (result == JFileChooser.APPROVE_OPTION) {
                    File saveFile = fileChooser.getSelectedFile();

                    // Gửi chấp nhận file đến server
                    client.acceptFileTransfer(fileId);

                    // Thêm progress bar cho download
                    JProgressBar progressBar = addDownloadProgressBar(fileName, sender);

                    // Lưu thông tin file sẽ download
                    client.addFileToDownload(fileId, sender, fileName, fileSize, saveFile.getAbsolutePath());
                }
            } else {
                // Từ chối file
                client.rejectFileTransfer(fileId);
            }
        });
    }

    public void handleFileAccepted(String fileId, String receiver) {
        SwingUtilities.invokeLater(() -> {
            File fileToSend = client.getFileToUpload(fileId);
            if (fileToSend != null) {
                // Thực hiện upload
                client.uploadFile(fileId, fileToSend, receiver, progress -> {
                    JProgressBar progressBar = uploadProgressBars.get(fileId);
                    if (progressBar != null) {
                        progressBar.setValue(progress);
                        if (progress >= 100) {
                            removeUploadProgressBar(fileId);
                        }
                    }
                });
            }
        });
    }

    public void handleFileReady(String fileId) {
        SwingUtilities.invokeLater(() -> {
            String[] fileInfo = client.getFileToDownload(fileId);
            if (fileInfo != null) {
                String sender = fileInfo[0];
                String fileName = fileInfo[1];
                long fileSize = Long.parseLong(fileInfo[2]);
                String savePath = fileInfo[3];

                // Thực hiện download
                client.downloadFile(fileId, sender, fileName, fileSize, savePath, progress -> {
                    JProgressBar progressBar = downloadProgressBars.get(fileId);
                    if (progressBar != null) {
                        progressBar.setValue(progress);
                        if (progress >= 100) {
                            removeDownloadProgressBar(fileId);
                        }
                    }
                });
            }
        });
    }

    // Thêm progress bar cho upload
    private JProgressBar addUploadProgressBar(String fileName, String receiver, String fileId) {
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        filePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        filePanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        JLabel fileLabel = new JLabel(fileName + " → " + receiver);
        fileLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        filePanel.add(fileLabel, BorderLayout.NORTH);
        filePanel.add(progressBar, BorderLayout.CENTER);

        uploadPanel.add(filePanel);
        uploadPanel.revalidate();
        uploadPanel.repaint();

        uploadProgressBars.put(fileId, progressBar);

        return progressBar;
    }

    // Thêm progress bar cho download
    private JProgressBar addDownloadProgressBar(String fileName, String sender) {
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        filePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        filePanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        JLabel fileLabel = new JLabel(fileName + " ← " + sender);
        fileLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        filePanel.add(fileLabel, BorderLayout.NORTH);
        filePanel.add(progressBar, BorderLayout.CENTER);

        downloadPanel.add(filePanel);
        downloadPanel.revalidate();
        downloadPanel.repaint();

        String fileId = fileName + "_" + System.currentTimeMillis();
        downloadProgressBars.put(fileId, progressBar);

        return progressBar;
    }

    public void updateUploadProgress(String fileId, int progress) {
        JProgressBar progressBar = uploadProgressBars.get(fileId);
        if (progressBar != null) {
            progressBar.setValue(progress);
            if (progress >= 100) {
                removeUploadProgressBar(fileId);
            }
        }
    }

    // Xóa progress bar upload khi hoàn thành
    private void removeUploadProgressBar(String fileId) {
        JProgressBar progressBar = uploadProgressBars.get(fileId);
        if (progressBar != null) {
            SwingUtilities.invokeLater(() -> {
                Container parent = progressBar.getParent();
                if (parent != null) {
                    Container grandparent = parent.getParent();
                    grandparent.remove(parent);
                    grandparent.revalidate();
                    grandparent.repaint();
                }
            });
            uploadProgressBars.remove(fileId);
        }
    }

    // Xóa progress bar download khi hoàn thành
    private void removeDownloadProgressBar(String fileId) {
        JProgressBar progressBar = downloadProgressBars.get(fileId);
        if (progressBar != null) {
            SwingUtilities.invokeLater(() -> {
                Container parent = progressBar.getParent();
                if (parent != null) {
                    Container grandparent = parent.getParent();
                    grandparent.remove(parent);
                    grandparent.revalidate();
                    grandparent.repaint();
                }
            });
            downloadProgressBars.remove(fileId);
        }
    }

    // Format kích thước file cho dễ đọc
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
