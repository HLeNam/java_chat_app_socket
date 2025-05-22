package ui;

import client.ChatClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class LoginFrame extends JFrame {
    private ChatClient client;
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JPanel serverPanel;
    private JTextField serverIPField;
    private JTextField serverPortField;
    private JButton connectButton;
    private JPanel loginPanel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JPanel registerPanel;
    private JTextField fullNameField;
    private JTextField emailField;
    private JButton registerButton;
    private JTextField registerUsernameField;
    private JPasswordField registerPasswordField;

    public LoginFrame(ChatClient client) {
        this.client = client;

        initGUI();
    }

    private void initGUI() {
        setTitle("Chat App - Đăng nhập");
        setSize(400, 400);
        setMinimumSize(new Dimension(400, 400));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        createServerPanel();

        createLoginPanel();

        createRegisterPanel();

        cardPanel.add(serverPanel, "SERVER");
        cardPanel.add(loginPanel, "LOGIN");
        cardPanel.add(registerPanel, "REGISTER");

        cardLayout.show(cardPanel, "SERVER");

        add(cardPanel);
    }

    private void createServerPanel() {
        serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 5, 5, 5);

        // Title
        JLabel titleLabel = new JLabel("Kết nối đến Server", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        serverPanel.add(titleLabel, constraints);

        // Server IP Address
        JLabel ipLabel = new JLabel("Địa chỉ IP Server:");
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        serverPanel.add(ipLabel, constraints);

        serverIPField = new JTextField("127.0.0.1");
        constraints.gridx = 1;
        constraints.gridy = 1;
        serverPanel.add(serverIPField, constraints);

        // Server Port
        JLabel portLabel = new JLabel("Cổng Server:");
        constraints.gridx = 0;
        constraints.gridy = 2;
        serverPanel.add(portLabel, constraints);

        serverPortField = new JTextField("9999");
        constraints.gridx = 1;
        constraints.gridy = 2;
        serverPanel.add(serverPortField, constraints);

        // Connect Button
        connectButton = new JButton("Kết nối");
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        serverPanel.add(connectButton, constraints);

        connectButton.addActionListener(e -> {
            String serverIP = serverIPField.getText().trim();
            int serverPort;

            try {
                serverPort = Integer.parseInt(serverPortField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Cổng server phải là số nguyên!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Hiển thị thông báo đang kết nối
            connectButton.setEnabled(false);
            connectButton.setText("Đang kết nối...");

            // Kết nối trong thread riêng để không block UI
            new Thread(() -> {
              try {
                  boolean connected = client.connectToServer(serverIP, serverPort);
                  System.out.println("Kết nối đến server: " + connected);
                  // Update UI trong EDT
                  SwingUtilities.invokeLater(() -> {
                      if (connected) {
                          // Chuyển sang màn hình đăng nhập
                          cardLayout.show(cardPanel, "LOGIN");
                      } else {
                          JOptionPane.showMessageDialog(this,
                                  "Không thể kết nối đến server!",
                                  "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                          connectButton.setEnabled(true);
                          connectButton.setText("Kết nối");
                      }
                  });
              } catch (Exception ex) {
                  ex.printStackTrace();
                  SwingUtilities.invokeLater(() -> {
                      JOptionPane.showMessageDialog(this,
                              ex.getMessage(),
                              "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                      connectButton.setEnabled(true);
                      connectButton.setText("Kết nối");
                  });
              }
            }).start();
        });
    }

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 5, 5, 5);

        // Title
        JLabel titleLabel = new JLabel("Đăng nhập", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        loginPanel.add(titleLabel, constraints);

        // Username
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        loginPanel.add(usernameLabel, constraints);
        
        usernameField = new JTextField();
        constraints.gridx = 1;
        constraints.gridy = 1;
        loginPanel.add(usernameField, constraints);

        // Password
        JLabel passwordLabel = new JLabel("Mật khẩu:");
        constraints.gridx = 0;
        constraints.gridy = 2;
        loginPanel.add(passwordLabel, constraints);

        passwordField = new JPasswordField();
        constraints.gridx = 1;
        constraints.gridy = 2;
        loginPanel.add(passwordField, constraints);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Login Button
        loginButton = new JButton("Đăng nhập");
        buttonPanel.add(loginButton);

        // Register Button
        JButton switchToRegisterButton = new JButton("Đăng ký tài khoản");
        buttonPanel.add(switchToRegisterButton);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        loginPanel.add(buttonPanel, constraints);

        // Add Action Listeners
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });

        switchToRegisterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(cardPanel, "REGISTER");
            }
        });

        KeyAdapter enterKeyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.getSource() == usernameField || e.getSource() == passwordField) {
                        handleLogin();
                    }
                }
            }
        };

        usernameField.addKeyListener(enterKeyListener);
        passwordField.addKeyListener(enterKeyListener);
    }

    private void createRegisterPanel() {
        registerPanel = new JPanel(new GridBagLayout());
        registerPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 5, 5, 5);

        // Title
        JLabel titleLabel = new JLabel("Đăng ký tài khoản", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        registerPanel.add(titleLabel, constraints);

        // Username
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        registerPanel.add(usernameLabel, constraints);

        registerUsernameField = new JTextField();
        constraints.gridx = 1;
        constraints.gridy = 1;
        registerPanel.add(registerUsernameField, constraints);

        // Password
        JLabel passwordLabel = new JLabel("Mật khẩu:");
        constraints.gridx = 0;
        constraints.gridy = 2;
        registerPanel.add(passwordLabel, constraints);

        registerPasswordField = new JPasswordField();
        constraints.gridx = 1;
        constraints.gridy = 2;
        registerPanel.add(registerPasswordField, constraints);

        // Full Name
        JLabel fullNameLabel = new JLabel("Họ và tên:");
        constraints.gridx = 0;
        constraints.gridy = 3;
        registerPanel.add(fullNameLabel, constraints);

        fullNameField = new JTextField();
        constraints.gridx = 1;
        constraints.gridy = 3;
        registerPanel.add(fullNameField, constraints);

        // Email
        JLabel emailLabel = new JLabel("Email:");
        constraints.gridx = 0;
        constraints.gridy = 4;
        registerPanel.add(emailLabel, constraints);

        emailField = new JTextField();
        constraints.gridx = 1;
        constraints.gridy = 4;
        registerPanel.add(emailField, constraints);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Register Button
        registerButton = new JButton("Đăng ký");
        buttonPanel.add(registerButton);

        // Switch to Login Button
        JButton switchToLoginButton = new JButton("Đã có tài khoản? Đăng nhập");
        buttonPanel.add(switchToLoginButton);

        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        registerPanel.add(buttonPanel, constraints);

        // Add Action Listeners
        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleRegister();
            }
        });

        switchToLoginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(cardPanel, "LOGIN");
            }
        });
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Đang đăng nhập...");

        client.initialize(username);

        client.login(username, password);

        SwingUtilities.invokeLater(() -> {
            loginButton.setEnabled(true);
            loginButton.setText("Đăng nhập");
        });
    }

    private void handleRegister() {
        String username = registerUsernameField.getText().trim();
        String password = new String(registerPasswordField.getPassword()).trim();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable register button
        registerButton.setEnabled(false);
        registerButton.setText("Đang đăng ký...");

        // Đăng ký
        client.register(username, password, fullName, email);

        SwingUtilities.invokeLater(() -> {
            registerButton.setEnabled(true);
            registerButton.setText("Đăng ký");
        });
    }
}
