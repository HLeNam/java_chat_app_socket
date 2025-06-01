package ui;

import client.ChatClient;
import client.media.VoiceCallManager;
import client.network.AudioClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.TimerTask;

public class VoiceCallDialog extends JDialog implements VoiceCallManager.VoiceCallListener {
    private static final long serialVersionUID = 1L;

    private ChatClient client;
    private String callParticipant;
    private JLabel statusLabel;
    private JLabel durationLabel;
    private RoundedButton endCallButton;
    private RoundedButton muteButton;
    private Timer callTimer;
    private int callDuration = 0;
    private boolean isIncoming;
    private boolean isMuted = false;
    private boolean isInCall = false;

    private JSlider micVolumeSlider;
    private JSlider speakerVolumeSlider;
    private JCheckBox muteCheckBox;
    private float currentMicGain = 2.5f;
    private float currentSpeakerGain = 2.0f;

    private JCheckBox agcCheckBox;
    private JPanel warningPanel;

    public VoiceCallDialog(Frame owner, ChatClient client, String callParticipant, boolean isIncoming) {
        super(owner, "Cuộc gọi thoại với " + callParticipant, false);
        this.client = client;
        this.callParticipant = callParticipant;
        this.isIncoming = isIncoming;

        // Đọc cài đặt âm lượng đã lưu
        VoiceCallManager.VolumeSettings settings = client.getVoiceCallManager().loadVolumeSettings();
        currentMicGain = settings.micGain;
        currentSpeakerGain = settings.speakerGain;

        initComponents();

        // Áp dụng cài đặt đã đọc
        micVolumeSlider.setValue((int)(currentMicGain * 10));
        speakerVolumeSlider.setValue((int)(currentSpeakerGain * 10));
        agcCheckBox.setSelected(settings.agcEnabled);

        // Vô hiệu hóa slider microphone nếu AGC được bật
        micVolumeSlider.setEnabled(!settings.agcEnabled && !muteCheckBox.isSelected());

        client.getVoiceCallManager().setCallListener(this);
    }

    private void initComponents() {
        setSize(400, 480);  // Giảm chiều cao một chút
        setLocationRelativeTo(getOwner());
        setResizable(false);  // Ngăn người dùng thay đổi kích thước

        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        contentPane.setLayout(new BorderLayout(5, 10));
        setContentPane(contentPane);

        warningPanel = new JPanel();
        warningPanel.setVisible(false); // Ẩn ban đầu
        contentPane.add(warningPanel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));

        JPanel userInfoPanel = createUserInfoPanel();
        mainPanel.add(userInfoPanel, BorderLayout.NORTH);

        JPanel audioControlsPanel = createAudioControlsPanel();
        mainPanel.add(audioControlsPanel, BorderLayout.CENTER);

        contentPane.add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = createButtonPanel();
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.getVoiceCallManager().endCall();
            }
        });
    }

    private JPanel createUserInfoPanel() {
        JPanel userPanel = new JPanel();
        userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));

        // Avatar
        AvatarPanel avatarPanel = new AvatarPanel(callParticipant);
        avatarPanel.setPreferredSize(new Dimension(80, 80));
        avatarPanel.setMaximumSize(new Dimension(80, 80));
        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Tên người dùng
        JLabel nameLabel = new JLabel(callParticipant);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Trạng thái cuộc gọi
        statusLabel = new JLabel(isIncoming ? "Đang gọi đến..." : "Đang gọi...");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));

        // Thời gian cuộc gọi
        durationLabel = new JLabel("00:00");
        durationLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        durationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        durationLabel.setVisible(false);

        JPanel avatarContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        avatarContainer.add(avatarPanel);

        userPanel.add(avatarContainer);
        userPanel.add(Box.createVerticalStrut(8));
        userPanel.add(nameLabel);
        userPanel.add(Box.createVerticalStrut(4));
        userPanel.add(statusLabel);
        userPanel.add(Box.createVerticalStrut(2));
        userPanel.add(durationLabel);
        userPanel.add(Box.createVerticalStrut(10));

        return userPanel;
    }

    private JPanel createAudioControlsPanel() {
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Điều chỉnh âm lượng",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.PLAIN, 12)
        ));

        // Micro panel
        JPanel micPanel = new JPanel(new BorderLayout(5, 0));
        JLabel micLabel = new JLabel("Micro:");
        micVolumeSlider = new JSlider(JSlider.HORIZONTAL, 10, 50, 25);
        micVolumeSlider.setMajorTickSpacing(10);
        micVolumeSlider.setPaintTicks(true);
        micVolumeSlider.setPaintLabels(true);
        micVolumeSlider.setPreferredSize(new Dimension(200, 40));

        micPanel.add(micLabel, BorderLayout.WEST);
        micPanel.add(micVolumeSlider, BorderLayout.CENTER);

        // Mute checkbox
        muteCheckBox = new JCheckBox("Tắt micro");
        JPanel mutePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        mutePanel.add(muteCheckBox);
        micPanel.add(mutePanel, BorderLayout.EAST);

        // Speaker panel
        JPanel speakerPanel = new JPanel(new BorderLayout(5, 0));
        JLabel speakerLabel = new JLabel("Loa:   ");
        speakerVolumeSlider = new JSlider(JSlider.HORIZONTAL, 10, 50, 20);
        speakerVolumeSlider.setMajorTickSpacing(10);
        speakerVolumeSlider.setPaintTicks(true);
        speakerVolumeSlider.setPaintLabels(true);
        speakerVolumeSlider.setPreferredSize(new Dimension(200, 40));

        speakerPanel.add(speakerLabel, BorderLayout.WEST);
        speakerPanel.add(speakerVolumeSlider, BorderLayout.CENTER);

        // AGC checkbox
        agcCheckBox = new JCheckBox("Tự động điều chỉnh âm lượng (AGC)");
        agcCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        controlsPanel.add(Box.createVerticalStrut(5));
        controlsPanel.add(micPanel);
        controlsPanel.add(Box.createVerticalStrut(10));
        controlsPanel.add(speakerPanel);
        controlsPanel.add(Box.createVerticalStrut(10));
        controlsPanel.add(agcCheckBox);
        controlsPanel.add(Box.createVerticalStrut(5));

        micVolumeSlider.addChangeListener(e -> {
            if (!micVolumeSlider.getValueIsAdjusting()) {
                currentMicGain = micVolumeSlider.getValue() / 10.0f;
                adjustMicGain();
            }
        });

        speakerVolumeSlider.addChangeListener(e -> {
            if (!speakerVolumeSlider.getValueIsAdjusting()) {
                currentSpeakerGain = speakerVolumeSlider.getValue() / 10.0f;
                adjustSpeakerGain();
            }
        });

        muteCheckBox.addActionListener(e -> {
            boolean muted = muteCheckBox.isSelected();
            client.getVoiceCallManager().setMuted(muted);
            micVolumeSlider.setEnabled(!muted && !agcCheckBox.isSelected());
            if (muteButton != null) {
                muteButton.setText(muted ? "Bật mic" : "Tắt mic");
            }
            isMuted = muted;
        });

        agcCheckBox.addActionListener(e -> {
            boolean agcEnabled = agcCheckBox.isSelected();
            if (client.getVoiceCallManager().getAudioClient() != null) {
                client.getVoiceCallManager().getAudioClient().setAGCEnabled(agcEnabled);
                micVolumeSlider.setEnabled(!agcEnabled && !muteCheckBox.isSelected());
            }
        });

        return controlsPanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));

        if (isIncoming) {
            RoundedButton acceptButton = new RoundedButton("Trả lời", new Color(76, 175, 80), Color.WHITE);
            RoundedButton rejectButton = new RoundedButton("Từ chối", new Color(234, 67, 53), Color.WHITE);

            acceptButton.setPreferredSize(new Dimension(100, 40));
            rejectButton.setPreferredSize(new Dimension(100, 40));

            acceptButton.addActionListener(e -> {
                client.getVoiceCallManager().acceptCall(callParticipant);
            });

            rejectButton.addActionListener(e -> {
                client.getVoiceCallManager().rejectCall(callParticipant);
                dispose();
            });

            buttonPanel.add(rejectButton);
            buttonPanel.add(acceptButton);
        } else {
            muteButton = new RoundedButton("Tắt mic", new Color(66, 133, 244), Color.WHITE);
            endCallButton = new RoundedButton("Kết thúc", new Color(234, 67, 53), Color.WHITE);

            muteButton.setPreferredSize(new Dimension(100, 40));
            endCallButton.setPreferredSize(new Dimension(100, 40));

            muteButton.addActionListener(e -> {
                isMuted = !isMuted;
                muteButton.setText(isMuted ? "Bật mic" : "Tắt mic");
                muteCheckBox.setSelected(isMuted);
                client.getVoiceCallManager().setMuted(isMuted);
            });

            endCallButton.addActionListener(e -> {
                client.getVoiceCallManager().endCall();
            });

            JButton helpButton = new JButton("Trợ giúp");
            helpButton.setPreferredSize(new Dimension(100, 40));
            helpButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this,
                        "• Sử dụng thanh trượt để điều chỉnh âm lượng của microphone và loa\n" +
                                "• Nếu bạn không nghe thấy đối phương, hãy tăng âm lượng loa\n" +
                                "• Nếu đối phương không nghe thấy bạn, hãy tăng âm lượng microphone\n" +
                                "• Bật 'Tự động điều chỉnh' để hệ thống tự điều chỉnh âm lượng microphone",
                        "Trợ giúp âm thanh",
                        JOptionPane.INFORMATION_MESSAGE);
            });

            buttonPanel.add(muteButton);
            buttonPanel.add(endCallButton);
            buttonPanel.add(helpButton);
        }

        return buttonPanel;
    }

    private void showConnectedUI() {
        durationLabel.setVisible(true);

        if (isIncoming) {
            JPanel buttonPanel = (JPanel)getContentPane().getComponent(2);
            buttonPanel.removeAll();

            muteButton = new RoundedButton("Tắt mic", new Color(66, 133, 244), Color.WHITE);
            endCallButton = new RoundedButton("Kết thúc", new Color(234, 67, 53), Color.WHITE);

            muteButton.setPreferredSize(new Dimension(100, 40));
            endCallButton.setPreferredSize(new Dimension(100, 40));

            muteButton.addActionListener(e -> {
                isMuted = !isMuted;
                muteButton.setText(isMuted ? "Bật mic" : "Tắt mic");
                muteCheckBox.setSelected(isMuted);
                client.getVoiceCallManager().setMuted(isMuted);
            });

            endCallButton.addActionListener(e -> {
                client.getVoiceCallManager().endCall();
            });

            JButton helpButton = new JButton("Trợ giúp");
            helpButton.setPreferredSize(new Dimension(100, 40));
            helpButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this,
                        "• Sử dụng thanh trượt để điều chỉnh âm lượng của microphone và loa\n" +
                                "• Nếu bạn không nghe thấy đối phương, hãy tăng âm lượng loa\n" +
                                "• Nếu đối phương không nghe thấy bạn, hãy tăng âm lượng microphone\n" +
                                "• Bật 'Tự động điều chỉnh' để hệ thống tự điều chỉnh âm lượng microphone",
                        "Trợ giúp âm thanh",
                        JOptionPane.INFORMATION_MESSAGE);
            });

            buttonPanel.add(muteButton);
            buttonPanel.add(endCallButton);
            buttonPanel.add(helpButton);
            buttonPanel.revalidate();
            buttonPanel.repaint();
        }

        // Start call timer
        callTimer = new Timer(1000, e -> {
            callDuration++;
            updateDurationLabel();
        });
        callTimer.start();
    }

    private void updateDurationLabel() {
        int minutes = callDuration / 60;
        int seconds = callDuration % 60;
        durationLabel.setText(String.format("%02d:%02d", minutes, seconds));
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

        // Thêm nút đóng
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

    private void adjustMicGain() {
        if (client.getVoiceCallManager().getAudioClient() != null) {
            client.getVoiceCallManager().getAudioClient().setRecordingGain(currentMicGain);
        }
    }

    private void adjustSpeakerGain() {
        if (client.getVoiceCallManager().getAudioClient() != null) {
            client.getVoiceCallManager().getAudioClient().setPlaybackGain(currentSpeakerGain);
        }
    }

    private void showAudioDeviceWarning() {
        boolean micAvailable = false;
        boolean speakerAvailable = false;

        if (client.getVoiceCallManager().getAudioClient() != null) {
            AudioClient audioClient = client.getVoiceCallManager().getAudioClient();
            micAvailable = audioClient.isMicrophoneAvailable();
            speakerAvailable = audioClient.isSpeakersAvailable();
        }

        if (!micAvailable && !speakerAvailable) {
            showWarningBanner("Không tìm thấy microphone và loa. Bạn sẽ không nghe được người khác và họ sẽ không nghe được bạn.");
        } else if (!micAvailable) {
            showWarningBanner("Không tìm thấy microphone. Đối phương sẽ không nghe được bạn.");
        } else if (!speakerAvailable) {
            showWarningBanner("Không tìm thấy loa. Bạn sẽ không nghe được đối phương.");
        }
    }

    public void showStatusMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            final String currentStatus = statusLabel.getText();

            statusLabel.setText(message);
            statusLabel.setForeground(Color.BLUE);

            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        if (isDisplayable()) {
                            statusLabel.setText(currentStatus);
                            statusLabel.setForeground(Color.BLACK);
                        }
                    });
                }
            }, 3000);
        });
    }

    // VoiceCallListener implementation
    @Override
    public void onCallStarting(String participant) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Đang gọi...");
        });
    }

    @Override
    public void onCallAccepted(String participant) {
        SwingUtilities.invokeLater(() -> {
            isInCall = true;
            statusLabel.setText("Đã kết nối");
            showConnectedUI();
        });
    }

    @Override
    public void onCallConnected(String participant) {
        SwingUtilities.invokeLater(() -> {
            isInCall = true;
            statusLabel.setText("Đã kết nối");
            showConnectedUI();

            // Áp dụng gain khi cuộc gọi được kết nối
            adjustMicGain();
            adjustSpeakerGain();

            // Kiểm tra và hiển thị cảnh báo về thiết bị âm thanh nếu cần
            showAudioDeviceWarning();
        });
    }

    @Override
    public void onCallRejected(String participant) {
        SwingUtilities.invokeLater(() -> {
            isInCall = false;
            statusLabel.setText("Cuộc gọi bị từ chối");
            statusLabel.setForeground(new Color(234, 67, 53));

            // Tự đóng dialog sau 2 giây
            Timer timer = new Timer(2000, e -> dispose());
            timer.setRepeats(false);
            timer.start();
        });
    }


    @Override
    public void onCallEnded(String participant) {
        SwingUtilities.invokeLater(() -> {
            isInCall = false;
            if (callTimer != null) {
                callTimer.stop();
            }

            statusLabel.setText("Cuộc gọi kết thúc");
            statusLabel.setForeground(new Color(234, 67, 53));

            // Tự đóng dialog sau 2 giây
            Timer timer = new Timer(2000, e -> dispose());
            timer.setRepeats(false);
            timer.start();
        });
    }

    @Override
    public void onStatusMessage(String message) {
        showStatusMessage(message);
    }

    @Override
    public boolean isDisplayable() {
        return super.isDisplayable() && isShowing();
    }

    @Override
    public void dispose() {
        // Lưu cài đặt âm lượng
        client.getVoiceCallManager().saveVolumeSettings(
                currentMicGain,
                currentSpeakerGain,
                agcCheckBox.isSelected()
        );

        super.dispose();
    }
}