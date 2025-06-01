package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class OutgoingCallDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private JLabel statusLabel;
    private RoundedButton cancelButton;
    private SoundPlayer soundPlayer = new SoundPlayer();
    private Timer animationTimer;
    private String[] animationFrames = {
            "Đang gọi .",
            "Đang gọi ..",
            "Đang gọi ..."
    };
    private int currentFrame = 0;

    public OutgoingCallDialog(Frame owner, String callee, boolean isVoiceCall, ActionListener cancelAction) {
        super(owner, isVoiceCall ? "Cuộc gọi thoại" : "Cuộc gọi video", false);

        setSize(300, 200);
        setLocationRelativeTo(owner);
        setResizable(false);

        initComponents(callee, isVoiceCall, cancelAction);

        // Phát âm thanh calling
        soundPlayer.playSound("sounds/calling.wav", true);

        // Bắt đầu hiệu ứng animation
        startAnimation();
    }

    private void initComponents(String callee, boolean isVoiceCall, ActionListener cancelAction) {
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setContentPane(contentPane);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(isVoiceCall ? "Đang gọi thoại" : "Đang gọi video");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel calleeLabel = new JLabel(callee);
        calleeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        calleeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel("Đang gọi ...");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        AvatarPanel avatarPanel = new AvatarPanel(callee);
        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(avatarPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(titleLabel);
        centerPanel.add(calleeLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(statusLabel);
        centerPanel.add(Box.createVerticalGlue());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        cancelButton = new RoundedButton("Huỷ cuộc gọi", new Color(234, 67, 53), Color.WHITE);

        cancelButton.addActionListener(e -> {
            if (cancelAction != null) {
                cancelAction.actionPerformed(e);
            }
            close();
        });

        buttonPanel.add(cancelButton);

        contentPane.add(centerPanel, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (cancelAction != null) {
                    cancelAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "windowClosing"));
                }
                close();
            }
        });
    }

    private void startAnimation() {
        animationTimer = new Timer(500, e -> {
            currentFrame = (currentFrame + 1) % animationFrames.length;
            statusLabel.setText(animationFrames[currentFrame]);
        });
        animationTimer.start();
    }

    public void setConnected() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        soundPlayer.stopSound();
        statusLabel.setText("Đã kết nối");
        setVisible(false);
        dispose();
    }

    public void setRejected() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        soundPlayer.stopSound();
        statusLabel.setText("Cuộc gọi bị từ chối");

        Timer closeTimer = new Timer(2000, e -> close());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    public void close() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        soundPlayer.stopSound();
        setVisible(false);
        dispose();
    }
}