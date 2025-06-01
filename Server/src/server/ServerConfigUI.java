package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ServerConfigUI extends JFrame {
    private JTextField hostField;
    private JTextField chatPortField;
    private JTextField filePortField;
    private JTextField audioPortField;
    private JTextField videoPortField;
    private JButton startButton;
    private JButton saveButton;
    private JTextArea logArea;
    private JScrollPane logScrollPane;
    private ChatServer chatServer;
    private boolean serverRunning = false;

    private String defaultHost = "0.0.0.0"; // Mặc định lắng nghe trên tất cả giao diện mạng
    private int defaultChatPort = 9999;
    private int defaultFilePort = 9998;
    private int defaultAudioPort = 9997;
    private int defaultVideoPort = 9996;

    private static final String CONFIG_FILE = "server_config.properties";

    public ServerConfigUI() {
        setTitle("Chat Server Configuration");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        loadConfig();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (serverRunning && chatServer != null) {
                    int option = JOptionPane.showConfirmDialog(
                            ServerConfigUI.this,
                            "Server đang chạy. Bạn có muốn dừng server trước khi thoát?",
                            "Xác nhận thoát",
                            JOptionPane.YES_NO_OPTION);

                    if (option == JOptionPane.YES_OPTION) {
                        try {
                            chatServer.stop();
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        });
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel configPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        configPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình Server"));

        configPanel.add(new JLabel("Địa chỉ Host:"));
        hostField = new JTextField(defaultHost);
        configPanel.add(hostField);

        configPanel.add(new JLabel("Port cho Chat:"));
        chatPortField = new JTextField(String.valueOf(defaultChatPort));
        configPanel.add(chatPortField);

        configPanel.add(new JLabel("Port cho File:"));
        filePortField = new JTextField(String.valueOf(defaultFilePort));
        configPanel.add(filePortField);

        configPanel.add(new JLabel("Port cho Audio:"));
        audioPortField = new JTextField(String.valueOf(defaultAudioPort));
        configPanel.add(audioPortField);

        configPanel.add(new JLabel("Port cho Video:"));
        videoPortField = new JTextField(String.valueOf(defaultVideoPort));
        configPanel.add(videoPortField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        startButton = new JButton("Khởi động Server");
        saveButton = new JButton("Lưu cấu hình");

        buttonPanel.add(startButton);
        buttonPanel.add(saveButton);

        logArea = new JTextArea();
        logArea.setEditable(false);

        initTextAreaWithUTF8Support();

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(500, 300));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(configPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(logScrollPane, BorderLayout.CENTER);

        startButton.addActionListener(e -> toggleServer());
        saveButton.addActionListener(e -> saveConfig());

        setContentPane(mainPanel);
    }

    private void initTextAreaWithUTF8Support() {
        Font textFont = new Font("Arial", Font.PLAIN, 12);
        logArea.setFont(textFont);
    }

    private void toggleServer() {
        if (!serverRunning) {
            startServer();
        } else {
            System.exit(0);
        }
    }

    private void startServer() {
        try {
            String host = hostField.getText().trim();
            int chatPort = Integer.parseInt(chatPortField.getText().trim());
            int filePort = Integer.parseInt(filePortField.getText().trim());
            int audioPort = Integer.parseInt(audioPortField.getText().trim());
            int videoPort = Integer.parseInt(videoPortField.getText().trim());

            validatePorts(chatPort, filePort, audioPort, videoPort);

            redirectSystemStreams();

            new Thread(() -> {
                try {
                    chatServer = new ChatServer(host, chatPort, filePort, audioPort, videoPort);
                    chatServer.start();

                    SwingUtilities.invokeLater(() -> {
                        serverRunning = true;
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        logArea.append("Lỗi khi khởi động server: " + ex.getMessage() + "\n");
                        startButton.setText("Khởi động Server");
                        setFieldsEnabled(true);
                        serverRunning = false;
                    });
                }
            }, "ServerStartThread").start();

            logArea.append("Server đang khởi động...\n");
            serverRunning = true;
            startButton.setText("Tắt Ứng Dụng");

            setFieldsEnabled(false);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Port phải là số nguyên!",
                    "Lỗi cấu hình",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Lỗi cấu hình",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void validatePorts(int... ports) {
        for (int port : ports) {
            if (port < 1024 || port > 65535) {
                throw new IllegalArgumentException("Port phải nằm trong khoảng từ 1024 đến 65535");
            }
        }

        for (int i = 0; i < ports.length - 1; i++) {
            for (int j = i + 1; j < ports.length; j++) {
                if (ports[i] == ports[j]) {
                    throw new IllegalArgumentException("Các port không được trùng nhau");
                }
            }
        }
    }

    private void setFieldsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        chatPortField.setEnabled(enabled);
        filePortField.setEnabled(enabled);
        audioPortField.setEnabled(enabled);
        videoPortField.setEnabled(enabled);
        saveButton.setEnabled(enabled);
    }

    private void redirectSystemStreams() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        PrintStream customOut = new PrintStream(new OutputStream() {
            private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) throws IOException {
                buffer.write(b);
                if (b == '\n' || buffer.size() > 1024) {
                    final String text = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset();

                    SwingUtilities.invokeLater(() -> {
                        logArea.append(text);
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });

                    originalOut.write(text.getBytes());
                }
            }

            @Override
            public void flush() throws IOException {
                if (buffer.size() > 0) {
                    final String text = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset();

                    SwingUtilities.invokeLater(() -> {
                        logArea.append(text);
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });

                    originalOut.write(text.getBytes());
                }
            }
        }, true, StandardCharsets.UTF_8);

        PrintStream customErr = new PrintStream(new OutputStream() {
            private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) throws IOException {
                buffer.write(b);
                if (b == '\n' || buffer.size() > 1024) {
                    final String text = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset(); // Xóa buffer

                    SwingUtilities.invokeLater(() -> {
                        logArea.append(text);
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });

                    originalErr.write(text.getBytes());
                }
            }

            @Override
            public void flush() throws IOException {
                if (buffer.size() > 0) {
                    final String text = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset();

                    SwingUtilities.invokeLater(() -> {
                        logArea.append(text);
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });

                    originalErr.write(text.getBytes());
                }
            }
        }, true, StandardCharsets.UTF_8);

        System.setOut(customOut);
        System.setErr(customErr);
    }

    private void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("host", hostField.getText().trim());
            props.setProperty("chatPort", chatPortField.getText().trim());
            props.setProperty("filePort", filePortField.getText().trim());
            props.setProperty("audioPort", audioPortField.getText().trim());
            props.setProperty("videoPort", videoPortField.getText().trim());

            File configFile = new File(CONFIG_FILE);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "Chat Server Configuration");
                JOptionPane.showMessageDialog(this,
                        "Cấu hình đã được lưu thành công!",
                        "Thông báo",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Không thể lưu cấu hình: " + e.getMessage(),
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                Properties props = new Properties();
                props.load(fis);

                hostField.setText(props.getProperty("host", defaultHost));
                chatPortField.setText(props.getProperty("chatPort", String.valueOf(defaultChatPort)));
                filePortField.setText(props.getProperty("filePort", String.valueOf(defaultFilePort)));
                audioPortField.setText(props.getProperty("audioPort", String.valueOf(defaultAudioPort)));
                videoPortField.setText(props.getProperty("videoPort", String.valueOf(defaultVideoPort)));

                logArea.append("Đã tải cấu hình từ " + CONFIG_FILE + "\n");
            } catch (IOException e) {
                e.printStackTrace();
                logArea.append("Không thể tải cấu hình: " + e.getMessage() + "\n");
            }
        } else {
            logArea.append("File cấu hình không tồn tại. Sử dụng giá trị mặc định.\n");
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            ServerConfigUI ui = new ServerConfigUI();
            ui.setVisible(true);
        });
    }
}