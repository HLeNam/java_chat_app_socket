package ui;

import client.ChatClient;
import client.media.VideoCallManager;
import client.network.VideoClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

public class VideoCallDialog
        extends JDialog
        implements VideoCallManager.VideoCallListener, VideoClient.VideoFrameCallback {
    private static final long serialVersionUID = 1L;

    private ChatClient client;
    private String callParticipant;
    private JLabel statusLabel;
    private JLabel durationLabel;
    private RoundedButton endCallButton;
    private RoundedButton muteButton;
    private RoundedButton videoButton;
    private Timer callTimer;
    private Timer monitorTimer;
    private int callDuration = 0;
    private boolean isIncoming;
    private boolean isMuted = false;
    private boolean isVideoEnabled = true;
    private boolean isInCall = false;

    private JSlider micVolumeSlider;
    private JSlider speakerVolumeSlider;
    private JCheckBox muteCheckBox;
    private JCheckBox agcCheckBox;
    private JPanel warningPanel;

    private VideoPanel localVideoPanel;
    private VideoPanel remoteVideoPanel;
    private BufferedImage localPlaceholderImage;
    private BufferedImage remotePlaceholderImage;
    private JLayeredPane layeredPane;

    private float currentMicGain = 2.0f;
    private float currentSpeakerGain = 2.0f;
    private int currentVideoQuality = 50;
    private int currentFrameRate = 15;

    private SoundPlayer soundPlayer = new SoundPlayer();
    private boolean isPlayingSound = false;

    private boolean isCameraInitialized = false;
    private Thread cameraInitThread;

    public VideoCallDialog(Frame owner, ChatClient client, String callParticipant, boolean isIncoming) {
        super(owner, "Cuộc gọi video với " + callParticipant, false);
        this.client = client;
        this.callParticipant = callParticipant;
        this.isIncoming = isIncoming;

        System.out.println("VideoCallDialog: Khởi tạo dialog cuộc gọi " +
                (isIncoming ? "đến" : "đi") + " với " + callParticipant);

        VideoCallManager.VolumeSettings settings = client.getVideoCallManager().loadVolumeSettings();
        currentMicGain = settings.micGain;
        currentSpeakerGain = settings.speakerGain;
        currentVideoQuality = settings.videoQuality;
        currentFrameRate = settings.frameRate;

        localPlaceholderImage = createPlaceholderImage(320, 240, "Không có video");
        remotePlaceholderImage = createPlaceholderImage(640, 480, "Đang chờ kết nối video...");

        initComponents();

        if (micVolumeSlider == null) {
            micVolumeSlider = new JSlider(JSlider.HORIZONTAL, 10, 50, (int)(currentMicGain * 10));
        }
        if (speakerVolumeSlider == null) {
            speakerVolumeSlider = new JSlider(JSlider.HORIZONTAL, 10, 50, (int)(currentSpeakerGain * 10));
        }
        if (agcCheckBox == null) {
            agcCheckBox = new JCheckBox("Tự động điều chỉnh âm lượng (AGC)");
        }

        micVolumeSlider.setValue((int)(currentMicGain * 10));
        speakerVolumeSlider.setValue((int)(currentSpeakerGain * 10));
        agcCheckBox.setSelected(settings.agcEnabled);

        client.getVideoCallManager().setCallListener(this);
        client.getVideoCallManager().setVideoFrameCallback(this);

        if (isIncoming) {
            playRingtone();
        } else {
            playCallingSound();
        }

        if (!isIncoming) {
            initializeCameraAsync();
        }

        startCallStateMonitor();
    }

    private void initializeCameraAsync() {
        updateStatusLabel("Đang kết nối camera...");

        cameraInitThread = new Thread(() -> {
            try {
                forceConnectVideo();
                isCameraInitialized = true;

                SwingUtilities.invokeLater(() -> {
                    updateStatusLabel("Đang gọi...");
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    showWarningBanner("Lỗi kết nối camera: " + e.getMessage());
                    updateStatusLabel("Lỗi kết nối camera");
                });
            }
        }, "CameraInitThread");

        cameraInitThread.start();
    }

    private void updateStatusLabel(String status) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(status);
            }
        });
    }

    private void playRingtone() {
        isPlayingSound = true;
        soundPlayer.playSound("/sounds/ringtone.wav", true);
    }

    private void playCallingSound() {
        isPlayingSound = true;
        soundPlayer.playSound("/sounds/calling.wav", true);
    }

    private void stopSound() {
        if (isPlayingSound) {
            soundPlayer.stopSound();
            isPlayingSound = false;
        }
    }

    private void startCallStateMonitor() {
        monitorTimer = new Timer(2000, e -> {
            try {
                if (isInCall && !isCallStillActive()) {
                    System.out.println("Phát hiện cuộc gọi đã kết thúc từ VideoCallManager, đóng dialog");
                    isInCall = false;
                    safelyDisposeDialog();
                }
            } catch (Exception ex) {
                System.err.println("Lỗi khi kiểm tra trạng thái cuộc gọi: " + ex.getMessage());
            }
        });
        monitorTimer.setRepeats(true);
        monitorTimer.start();
    }

    private boolean isCallStillActive() {
        if (client == null || client.getVideoCallManager() == null) {
            return false;
        }

        return client.getVideoCallManager().isInCall() &&
                callParticipant.equals(client.getVideoCallManager().getCurrentCallParticipant());
    }

    private void initComponents() {
        setSize(800, 600);
        setLocationRelativeTo(getOwner());

        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        contentPane.setLayout(new BorderLayout(5, 10));
        setContentPane(contentPane);

        warningPanel = new JPanel();
        warningPanel.setVisible(false);
        contentPane.add(warningPanel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));

        JPanel videoPanel = createVideoPanel();
        mainPanel.add(videoPanel, BorderLayout.CENTER);

        JPanel infoPanel = createInfoPanel();
        mainPanel.add(infoPanel, BorderLayout.SOUTH);

        contentPane.add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = createButtonPanel();
        buttonPanel.setName("buttonPanel");
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    client.getVideoCallManager().endCall();
                    safelyDisposeDialog();
                } catch (Exception ex) {
                    System.err.println("Lỗi khi đóng cửa sổ: " + ex.getMessage());
                    dispose();
                }
            }
        });
    }

    private JPanel createVideoPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        remoteVideoPanel = new VideoPanel();
        remoteVideoPanel.setPreferredSize(new Dimension(640, 480));
        remoteVideoPanel.setImage(remotePlaceholderImage);

        localVideoPanel = new VideoPanel();
        localVideoPanel.setPreferredSize(new Dimension(160, 120));
        localVideoPanel.setImage(localPlaceholderImage);

        layeredPane = new JLayeredPane() {
            // Override để đảm bảo các component con được vẽ đúng kích thước
            @Override
            public void doLayout() {
                super.doLayout();
                int width = getWidth();
                int height = getHeight();

                if (remoteVideoPanel != null) {
                    remoteVideoPanel.setBounds(0, 0, width, height);
                }

                if (localVideoPanel != null) {
                    int localWidth = Math.min(160, width / 4);
                    int localHeight = Math.min(120, height / 4);
                    localVideoPanel.setBounds(width - localWidth - 10, height - localHeight - 10,
                            localWidth, localHeight);
                }
            }
        };
        layeredPane.setPreferredSize(new Dimension(640, 480));

        remoteVideoPanel.setBounds(0, 0, 640, 480);
        localVideoPanel.setBounds(640 - 170, 480 - 130, 160, 120);

        layeredPane.add(remoteVideoPanel, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(localVideoPanel, JLayeredPane.PALETTE_LAYER);

        panel.add(layeredPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel nameLabel = new JLabel(callParticipant);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        userPanel.add(nameLabel);

        statusLabel = new JLabel(isIncoming ? "Đang gọi đến..." : "Đang kết nối camera...");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusPanel.add(statusLabel);

        durationLabel = new JLabel("00:00");
        durationLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        durationLabel.setVisible(false);
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        durationPanel.add(durationLabel);

        panel.add(userPanel);
        panel.add(statusPanel);
        panel.add(durationPanel);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));
        panel.setName("buttonPanel");

        if (isIncoming) {
            RoundedButton acceptButton = new RoundedButton("Trả lời", new Color(76, 175, 80), Color.WHITE);
            RoundedButton rejectButton = new RoundedButton("Từ chối", new Color(234, 67, 53), Color.WHITE);

            acceptButton.setPreferredSize(new Dimension(100, 40));
            rejectButton.setPreferredSize(new Dimension(100, 40));

            acceptButton.addActionListener(e -> {
                try {
                    System.out.println("VideoCallDialog: Người dùng chấp nhận cuộc gọi từ " + callParticipant);
                    stopSound();
                    client.getVideoCallManager().acceptCall(callParticipant);
                    isInCall = true;

                    initializeCameraAsync();

                    showConnectedUI();
                } catch (Exception ex) {
                    System.err.println("Lỗi khi chấp nhận cuộc gọi: " + ex.getMessage());
                    ex.printStackTrace();
                    showWarningBanner("Lỗi khi chấp nhận cuộc gọi: " + ex.getMessage());
                }
            });

            rejectButton.addActionListener(e -> {
                System.out.println("VideoCallDialog: Người dùng từ chối cuộc gọi từ " + callParticipant);
                stopSound();
                client.getVideoCallManager().rejectCall(callParticipant);
                safelyDisposeDialog();
            });

            panel.add(rejectButton);
            panel.add(acceptButton);
        } else {
            createCallControlButtons(panel);
        }

        return panel;
    }

    private void createCallControlButtons(JPanel panel) {
        try {
            panel.removeAll();

            muteButton = new RoundedButton("Tắt mic", new Color(66, 133, 244), Color.WHITE);
            videoButton = new RoundedButton("Tắt camera", new Color(66, 133, 244), Color.WHITE);
            endCallButton = new RoundedButton("Kết thúc", new Color(234, 67, 53), Color.WHITE);

            muteButton.setPreferredSize(new Dimension(100, 40));
            videoButton.setPreferredSize(new Dimension(100, 40));
            endCallButton.setPreferredSize(new Dimension(100, 40));

            muteButton.addActionListener(e -> {
                isMuted = !isMuted;
                muteButton.setText(isMuted ? "Bật mic" : "Tắt mic");
                client.getVideoCallManager().setMuted(isMuted);
            });

            videoButton.addActionListener(e -> {
                isVideoEnabled = !isVideoEnabled;
                videoButton.setText(isVideoEnabled ? "Tắt camera" : "Bật camera");
                client.getVideoCallManager().setVideoEnabled(isVideoEnabled);

                if (!isVideoEnabled) {
                    localVideoPanel.setImage(localPlaceholderImage);
                } else {
                    captureLocalFrame();
                }
            });

            endCallButton.addActionListener(e -> {
                try {
                    System.out.println("VideoCallDialog: Người dùng nhấn nút kết thúc cuộc gọi");

                    isInCall = false;

                    stopSound();

                    if (client != null && client.getVideoCallManager() != null) {
                        client.getVideoCallManager().endCall();
                    } else {
                        System.err.println("Warning: VideoCallManager là null khi kết thúc cuộc gọi");
                    }

                    statusLabel.setText("Cuộc gọi kết thúc");
                    statusLabel.setForeground(new Color(234, 67, 53));

                    Timer closeTimer = new Timer(1000, event -> {
                        safelyDisposeDialog();
                    });
                    closeTimer.setRepeats(false);
                    closeTimer.start();
                } catch (Exception ex) {
                    System.err.println("Lỗi khi kết thúc cuộc gọi: " + ex.getMessage());
                    ex.printStackTrace();
                    safelyDisposeDialog();
                }
            });

            JButton settingsButton = new JButton("Cài đặt");
            settingsButton.setPreferredSize(new Dimension(100, 40));
            settingsButton.addActionListener(e -> {
                showSettingsDialog();
            });

            panel.add(muteButton);
            panel.add(videoButton);
            panel.add(endCallButton);
            panel.add(settingsButton);

            panel.revalidate();
            panel.repaint();

            System.out.println("Đã tạo các nút điều khiển cuộc gọi");
        } catch (Exception e) {
            System.err.println("Lỗi khi tạo nút điều khiển cuộc gọi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void captureLocalFrame() {
        try {
            VideoClient videoClient = client.getVideoCallManager().getVideoClient();
            if (videoClient != null && videoClient.isWebcamAvailable()) {
                BufferedImage frame = videoClient.captureOneFrame();
                if (frame != null) {
                    localVideoPanel.setImage(frame);
                    System.out.println("Đã chụp và hiển thị một frame từ webcam local");
                } else {
                    System.err.println("Không thể chụp frame từ webcam local");
                }
            } else {
                System.err.println("VideoClient không khả dụng hoặc không có webcam");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi chụp frame từ webcam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void requestLocalVideoUpdate() {
        try {
            VideoClient videoClient = client.getVideoCallManager().getVideoClient();
            if (videoClient != null) {
                System.out.println("Yêu cầu cập nhật webcam local");
                if (isVideoEnabled) {
                    if (!videoClient.isSending()) {
                        videoClient.startCapturing();
                    } else {
                        captureLocalFrame();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi yêu cầu cập nhật video: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeLocalVideo() {
        try {
            VideoClient videoClient = client.getVideoCallManager().getVideoClient();
            if (videoClient != null) {
                if (videoClient.isWebcamAvailable()) {
                    System.out.println("Webcam khả dụng, đang khởi tạo video local");

                    isVideoEnabled = true;
                    if (videoButton != null) {
                        videoButton.setText("Tắt camera");
                    }

                    if (!videoClient.isSending()) {
                        videoClient.startCapturing();
                    } else {
                        captureLocalFrame();
                    }
                } else {
                    System.out.println("Webcam không khả dụng, hiển thị placeholder");
                    localVideoPanel.setImage(localPlaceholderImage);
                    isVideoEnabled = false;
                    if (videoButton != null) {
                        videoButton.setText("Bật camera");
                    }
                }
            } else {
                System.err.println("VideoClient chưa được khởi tạo, không thể khởi tạo video local");
                localVideoPanel.setImage(localPlaceholderImage);
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi khởi tạo video local: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void forceConnectVideo() {
        System.out.println("VideoCallDialog: Cưỡng chế kết nối video...");

        try {
            VideoCallManager manager = client.getVideoCallManager();
            if (manager != null) {
                boolean videoOk = manager.ensureVideoClient();
                boolean audioOk = manager.ensureAudioClient();

                if (!videoOk) {
                    System.err.println("VideoCallDialog: Không thể khởi tạo VideoClient!");
                    showWarningBanner("Không thể kết nối video! Hãy thử lại.");
                    return;
                }

                if (!audioOk) {
                    System.err.println("VideoCallDialog: Không thể khởi tạo AudioClient!");
                    showWarningBanner("Không thể kết nối âm thanh! Hãy thử lại.");
                }

                VideoClient videoClient = manager.getVideoClient();
                if (videoClient != null) {
                    if (!videoClient.isConnected()) {
                        System.out.println("VideoCallDialog: Kết nối lại VideoClient...");
                        videoClient.connect();
                    }

                    if (videoClient.isWebcamAvailable()) {
                        if (!isVideoEnabled) {
                            isVideoEnabled = true;
                            if (videoButton != null) {
                                videoButton.setText("Tắt camera");
                            }
                            manager.setVideoEnabled(true);
                        }

                        System.out.println("VideoCallDialog: Bắt đầu gửi video...");
                        if (!videoClient.isSending()) {
                            videoClient.startCapturing();
                        }

                        requestLocalVideoUpdate();

                        System.out.println("VideoCallDialog: Video đã được thiết lập.");
                    } else {
                        System.err.println("VideoCallDialog: Không tìm thấy webcam!");
                        showWarningBanner("Không tìm thấy webcam! Đối tác sẽ không thấy video của bạn.");
                        isVideoEnabled = false;
                        if (videoButton != null) {
                            videoButton.setText("Bật camera");
                        }
                        localVideoPanel.setImage(localPlaceholderImage);
                    }
                } else {
                    System.err.println("VideoCallDialog: VideoClient là null!");
                }

                if (isIncoming && isInCall) {
                    SwingUtilities.invokeLater(() -> {
                        showConnectedUI();

                        initializeLocalVideo();
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi cưỡng chế kết nối video: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showSettingsDialog() {
        JDialog settingsDialog = new JDialog(this, "Cài đặt cuộc gọi", true);
        settingsDialog.setSize(400, 450);
        settingsDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel audioPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        audioPanel.setBorder(BorderFactory.createTitledBorder("Âm thanh"));

        JPanel micPanel = new JPanel(new BorderLayout(5, 0));
        JLabel micLabel = new JLabel("Micro:");
        micVolumeSlider = new JSlider(JSlider.HORIZONTAL, 10, 50, (int)(currentMicGain * 10));
        micVolumeSlider.setMajorTickSpacing(10);
        micVolumeSlider.setPaintTicks(true);
        micVolumeSlider.setPaintLabels(true);

        micPanel.add(micLabel, BorderLayout.WEST);
        micPanel.add(micVolumeSlider, BorderLayout.CENTER);

        JPanel speakerPanel = new JPanel(new BorderLayout(5, 0));
        JLabel speakerLabel = new JLabel("Loa:   ");
        speakerVolumeSlider = new JSlider(JSlider.HORIZONTAL, 10, 50, (int)(currentSpeakerGain * 10));
        speakerVolumeSlider.setMajorTickSpacing(10);
        speakerVolumeSlider.setPaintTicks(true);
        speakerVolumeSlider.setPaintLabels(true);

        speakerPanel.add(speakerLabel, BorderLayout.WEST);
        speakerPanel.add(speakerVolumeSlider, BorderLayout.CENTER);

        agcCheckBox = new JCheckBox("Tự động điều chỉnh âm lượng (AGC)");

        audioPanel.add(micPanel);
        audioPanel.add(speakerPanel);
        audioPanel.add(agcCheckBox);

        JPanel videoPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        videoPanel.setBorder(BorderFactory.createTitledBorder("Video"));

        JPanel qualityPanel = new JPanel(new BorderLayout(5, 0));
        JLabel qualityLabel = new JLabel("Chất lượng:");
        JSlider qualitySlider = new JSlider(JSlider.HORIZONTAL, 10, 100, currentVideoQuality);
        qualitySlider.setMajorTickSpacing(20);
        qualitySlider.setPaintTicks(true);
        qualitySlider.setPaintLabels(true);

        qualityPanel.add(qualityLabel, BorderLayout.WEST);
        qualityPanel.add(qualitySlider, BorderLayout.CENTER);

        JPanel fpsPanel = new JPanel(new BorderLayout(5, 0));
        JLabel fpsLabel = new JLabel("Tốc độ khung hình:");
        String[] fpsOptions = {"5 FPS", "10 FPS", "15 FPS", "20 FPS", "25 FPS", "30 FPS"};
        JComboBox<String> fpsComboBox = new JComboBox<>(fpsOptions);
        fpsComboBox.setSelectedItem(currentFrameRate + " FPS");

        fpsPanel.add(fpsLabel, BorderLayout.WEST);
        fpsPanel.add(fpsComboBox, BorderLayout.CENTER);

        videoPanel.add(qualityPanel);
        videoPanel.add(fpsPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Hủy");
        JButton saveButton = new JButton("Lưu");

        cancelButton.addActionListener(e -> settingsDialog.dispose());

        saveButton.addActionListener(e -> {
            currentMicGain = micVolumeSlider.getValue() / 10.0f;
            currentSpeakerGain = speakerVolumeSlider.getValue() / 10.0f;
            boolean agcEnabled = agcCheckBox.isSelected();
            currentVideoQuality = qualitySlider.getValue();
            currentFrameRate = Integer.parseInt(((String)fpsComboBox.getSelectedItem()).split(" ")[0]);

            if (client.getVideoCallManager().getAudioClient() != null) {
                client.getVideoCallManager().getAudioClient().setRecordingGain(currentMicGain);
                client.getVideoCallManager().getAudioClient().setPlaybackGain(currentSpeakerGain);
                client.getVideoCallManager().getAudioClient().setAGCEnabled(agcEnabled);
            }

            if (client.getVideoCallManager().getVideoClient() != null) {
                client.getVideoCallManager().getVideoClient().setQuality(currentVideoQuality);
                client.getVideoCallManager().getVideoClient().setFrameRate(currentFrameRate);
            }

            settingsDialog.dispose();
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);

        panel.add(audioPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(videoPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(buttonPanel);

        settingsDialog.setContentPane(panel);
        settingsDialog.setVisible(true);
    }

    private void showConnectedUI() {
        System.out.println("VideoCallDialog: Hiển thị UI đã kết nối");

        try {
            if (client.getVideoCallManager().getVideoClient() == null) {
                System.err.println("VideoCallDialog: VideoClient chưa được khởi tạo khi hiển thị UI kết nối!");
                forceConnectVideo();
            }

            durationLabel.setVisible(true);

            JPanel buttonPanel = findButtonPanel();

            if (buttonPanel != null) {
                createCallControlButtons(buttonPanel);
                System.out.println("UI cuộc gọi đã được cập nhật");
            } else {
                System.err.println("Không tìm thấy buttonPanel trong showConnectedUI!");
                JPanel newButtonPanel = new JPanel();
                newButtonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));
                newButtonPanel.setName("buttonPanel");
                createCallControlButtons(newButtonPanel);
                getContentPane().add(newButtonPanel, BorderLayout.SOUTH);
                getContentPane().validate();
                getContentPane().repaint();
                System.out.println("Đã tạo mới buttonPanel");
            }

            if (callTimer != null) {
                callTimer.stop();
            }
            callTimer = new Timer(1000, e -> {
                callDuration++;
                updateDurationLabel();
            });
            callTimer.start();

        } catch (Exception e) {
            System.err.println("Lỗi khi cập nhật giao diện: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private JPanel findButtonPanel() {
        Container contentPane = getContentPane();

        for (Component comp : contentPane.getComponents()) {
            if (comp instanceof JPanel && "buttonPanel".equals(comp.getName())) {
                return (JPanel) comp;
            }
        }

        for (Component comp : contentPane.getComponents()) {
            if (comp instanceof JPanel) {
                Object constraint = ((BorderLayout)contentPane.getLayout()).getConstraints(comp);
                if (BorderLayout.SOUTH.equals(constraint)) {
                    return (JPanel)comp;
                }
            }
        }

        return null;
    }

    private void updateRemoteVideo() {
        if (isIncoming && isInCall) {
            SwingUtilities.invokeLater(() -> {
                try {
                    JPanel buttonPanel = findButtonPanel();

                    if (buttonPanel != null) {
                        boolean foundAcceptButton = false;

                        for (Component comp : buttonPanel.getComponents()) {
                            if (comp instanceof RoundedButton) {
                                RoundedButton btn = (RoundedButton) comp;
                                if ("Trả lời".equals(btn.getText())) {
                                    foundAcceptButton = true;
                                    break;
                                }
                            }
                        }

                        if (foundAcceptButton) {
                            System.out.println("Phát hiện giao diện chưa chuyển, thử chuyển lại...");
                            createCallControlButtons(buttonPanel);

                            // Khởi tạo lại các thành phần khác của giao diện
                            durationLabel.setVisible(true);
                            if (callTimer == null || !callTimer.isRunning()) {
                                callTimer = new Timer(1000, e -> {
                                    callDuration++;
                                    updateDurationLabel();
                                });
                                callTimer.start();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi khi cập nhật giao diện từ remote video: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    private void updateDurationLabel() {
        int minutes = callDuration / 60;
        int seconds = callDuration % 60;
        durationLabel.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private BufferedImage createPlaceholderImage(int width, int height, String text) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        int x = (width - textWidth) / 2;
        int y = (height - textHeight) / 2 + metrics.getAscent();
        g2d.drawString(text, x, y);

        g2d.dispose();
        return image;
    }

    private void showWarningBanner(String warningText) {
        warningPanel.removeAll();
        warningPanel.setBackground(new Color(255, 240, 200));
        warningPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 165, 32)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        warningPanel.setLayout(new BorderLayout());

        JLabel warningLabel = new JLabel("<html>" + warningText + "</html>");
        warningLabel.setForeground(new Color(180, 80, 0));
        warningLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
        warningLabel.setIconTextGap(10);
        warningPanel.add(warningLabel, BorderLayout.CENTER);

        JButton closeButton = new JButton("×");
        closeButton.setForeground(new Color(180, 80, 0));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        closeButton.addActionListener(e -> warningPanel.setVisible(false));

        warningPanel.add(closeButton, BorderLayout.EAST);
        warningPanel.setVisible(true);

        getContentPane().revalidate();
    }

    public void showStatusMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                final String currentStatus = statusLabel.getText();

                statusLabel.setText(message);
                statusLabel.setForeground(Color.BLUE);

                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                if (isDisplayable()) {
                                    statusLabel.setText(currentStatus);
                                    statusLabel.setForeground(Color.BLACK);
                                }
                            } catch (Exception e) {
                            }
                        });
                    }
                }, 3000);
            } catch (Exception e) {
                System.err.println("Lỗi khi hiển thị thông báo: " + e.getMessage());
            }
        });
    }

    @Override
    public void onCallStarting(String participant) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Đang gọi...");
        });
    }

    @Override
    public void onCallReceived(String participant) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Cuộc gọi đến từ " + participant);
        });
    }

    @Override
    public void onCallAccepted(String participant) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("VideoCallDialog: Cuộc gọi đã được chấp nhận, đang thiết lập kết nối...");

                stopSound();

                isInCall = true;
                statusLabel.setText("Đang kết nối...");

                initializeCameraAsync();

                if (isIncoming) {
                    System.out.println("Đây là cuộc gọi đến, đang chuyển đổi giao diện...");
                    showConnectedUI();
                }
            } catch (Exception e) {
                System.err.println("Lỗi trong onCallAccepted: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onCallConnected(String participant) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("VideoCallDialog: Cuộc gọi đã kết nối!");

                stopSound();

                isInCall = true;
                statusLabel.setText("Đã kết nối");

                showConnectedUI();

                checkWebcamAvailability();

                initializeLocalVideo();
            } catch (Exception e) {
                System.err.println("Lỗi trong onCallConnected: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onCallRejected(String participant) {
        SwingUtilities.invokeLater(() -> {
            stopSound();

            isInCall = false;
            statusLabel.setText("Cuộc gọi bị từ chối");
            statusLabel.setForeground(new Color(234, 67, 53));

            Timer timer = new Timer(2000, e -> safelyDisposeDialog());
            timer.setRepeats(false);
            timer.start();
        });
    }

    @Override
    public void onCallEnded(String participant) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("VideoCallDialog: onCallEnded được gọi với participant: " + participant);

                stopSound();

                isInCall = false;

                if (callTimer != null) {
                    callTimer.stop();
                    callTimer = null;
                }

                statusLabel.setText("Cuộc gọi kết thúc");
                statusLabel.setForeground(new Color(234, 67, 53));

                localVideoPanel.setImage(localPlaceholderImage);
                remoteVideoPanel.setImage(remotePlaceholderImage);

                final Timer closeTimer = new Timer(1500, e -> {
                    try {
                        System.out.println("VideoCallDialog: Đóng dialog sau khi cuộc gọi kết thúc");
                        safelyDisposeDialog();
                    } catch (Exception ex) {
                        System.err.println("Lỗi khi đóng dialog: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
                closeTimer.setRepeats(false);
                closeTimer.start();

            } catch (Exception e) {
                System.err.println("Lỗi trong onCallEnded: " + e.getMessage());
                e.printStackTrace();
                safelyDisposeDialog();
            }
        });
    }

    @Override
    public void onCallStateChanged(VideoCallManager.CallState newState, String participant) {
        SwingUtilities.invokeLater(() -> {
            try {
                switch (newState) {
                    case IDLE:
                        statusLabel.setText("Trạng thái chờ");
                        break;
                    case CALLING:
                        statusLabel.setText("Đang gọi...");
                        break;
                    case RINGING:
                        statusLabel.setText("Đang đổ chuông...");
                        break;
                    case CONNECTED:
                        statusLabel.setText("Đã kết nối");
                        break;
                    case RECONNECTING:
                        statusLabel.setText("Đang kết nối lại...");
                        statusLabel.setForeground(new Color(255, 153, 0));
                        break;
                    case ENDED:
                        statusLabel.setText("Cuộc gọi kết thúc");
                        statusLabel.setForeground(new Color(234, 67, 53));

                        stopSound();

                        if (isInCall) {
                            isInCall = false;
                            Timer endTimer = new Timer(1500, e -> safelyDisposeDialog());
                            endTimer.setRepeats(false);
                            endTimer.start();
                        }
                        break;
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi xử lý thay đổi trạng thái: " + e.getMessage());
            }
        });
    }

    @Override
    public void onVideoToggled(String participant) {
        SwingUtilities.invokeLater(() -> {
            showStatusMessage(participant + " đã " +
                    (isVideoEnabled ? "tắt camera" : "bật camera"));

            if (!isVideoEnabled) {
                remoteVideoPanel.setImage(remotePlaceholderImage);
            }
        });
    }

    @Override
    public void onStatusMessage(String message) {
        showStatusMessage(message);
    }

    @Override
    public void onFrameReceived(BufferedImage frame) {
        if (frame == null) {
            System.err.println("VideoCallDialog: Nhận được frame null!");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                boolean isLocalFrame = (frame.getWidth() <= 320 && frame.getHeight() <= 240);

                if (isLocalFrame) {
                    localVideoPanel.setImage(frame);

                    if (!localVideoPanel.isVisible()) {
                        localVideoPanel.setVisible(true);
                    }

                    if (layeredPane != null) {
                        layeredPane.setLayer(localVideoPanel, JLayeredPane.PALETTE_LAYER);
                    }
                } else {
                    remoteVideoPanel.setImage(frame);

                    updateRemoteVideo();
                }

                if (layeredPane != null) {
                    layeredPane.validate();
                    layeredPane.repaint();
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi cập nhật frame: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void checkWebcamAvailability() {
        try {
            if (client.getVideoCallManager().getVideoClient() != null) {
                boolean webcamAvailable = client.getVideoCallManager().getVideoClient().isWebcamAvailable();
                if (!webcamAvailable) {
                    showWarningBanner("Không tìm thấy webcam. Đối phương sẽ không thấy hình ảnh của bạn.");
                    isVideoEnabled = false;
                    if (videoButton != null) {
                        videoButton.setText("Bật camera");
                    }
                } else {
                    System.out.println("VideoCallDialog: Đã phát hiện webcam, sẵn sàng truyền video.");
                }
            } else {
                System.err.println("VideoCallDialog: VideoClient chưa được khởi tạo khi kiểm tra webcam!");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi kiểm tra webcam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void safelyDisposeDialog() {
        try {
            System.out.println("VideoCallDialog: Đang đóng dialog an toàn");

            stopSound();

            if (callTimer != null) {
                callTimer.stop();
                callTimer = null;
            }

            if (monitorTimer != null) {
                monitorTimer.stop();
                monitorTimer = null;
            }

            if (cameraInitThread != null && cameraInitThread.isAlive()) {
                cameraInitThread.interrupt();
                cameraInitThread = null;
            }

            if (client != null && client.getVideoCallManager() != null && agcCheckBox != null) {
                client.getVideoCallManager().saveVolumeSettings(
                        currentMicGain,
                        currentSpeakerGain,
                        agcCheckBox.isSelected(),
                        currentVideoQuality,
                        currentFrameRate
                );
            }

            setVisible(false);
            dispose();

            System.out.println("VideoCallDialog: Dialog đã đóng thành công");
        } catch (Exception e) {
            System.err.println("Lỗi khi đóng dialog an toàn: " + e.getMessage());
            e.printStackTrace();
            try {
                dispose();
            } catch (Exception ex) {
            }
        }
    }

    @Override
    public boolean isDisplayable() {
        return super.isDisplayable() && isShowing();
    }

    @Override
    public void dispose() {
        try {
            System.out.println("VideoCallDialog: Phương thức dispose() được gọi");

            stopSound();

            if (callTimer != null && callTimer.isRunning()) {
                callTimer.stop();
                callTimer = null;
            }

            if (monitorTimer != null && monitorTimer.isRunning()) {
                monitorTimer.stop();
                monitorTimer = null;
            }

            if (cameraInitThread != null && cameraInitThread.isAlive()) {
                cameraInitThread.interrupt();
                cameraInitThread = null;
            }

            if (client != null && client.getVideoCallManager() != null && agcCheckBox != null) {
                client.getVideoCallManager().saveVolumeSettings(
                        currentMicGain,
                        currentSpeakerGain,
                        agcCheckBox.isSelected(),
                        currentVideoQuality,
                        currentFrameRate
                );
            }

            super.dispose();
            System.out.println("VideoCallDialog: Dialog đã đóng thành công qua dispose()");
        } catch (Exception e) {
            System.err.println("Lỗi khi dispose dialog: " + e.getMessage());
            e.printStackTrace();
            try {
                super.dispose();
            } catch (Exception ex) {
            }
        }
    }
}