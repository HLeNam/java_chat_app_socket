package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class CallNotificationDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    public static final int ACCEPT = 0;
    public static final int REJECT = 1;

    private int result = REJECT;
    private JLabel callerLabel;
    private JLabel typeLabel;
    private Timer blinkTimer;
    private boolean isRed = false;

    public CallNotificationDialog(Frame owner, String caller, boolean isVoiceCall) {
        super(owner, "Cuộc gọi đến", true); // Modal dialog

        setSize(300, 200);
        setLocationRelativeTo(owner);
        setResizable(false);

        initComponents(caller, isVoiceCall);

        startBlinkEffect();
    }

    private void initComponents(String caller, boolean isVoiceCall) {
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setContentPane(contentPane);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        typeLabel = new JLabel(isVoiceCall ? "Cuộc gọi thoại đến" : "Cuộc gọi video đến");
        typeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        typeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        callerLabel = new JLabel("từ " + caller);
        callerLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        callerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        AvatarPanel avatarPanel = new AvatarPanel(caller);
        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(avatarPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(typeLabel);
        centerPanel.add(callerLabel);
        centerPanel.add(Box.createVerticalGlue());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        RoundedButton acceptButton = new RoundedButton("Trả lời", new Color(76, 175, 80), Color.WHITE);
        RoundedButton rejectButton = new RoundedButton("Từ chối", new Color(234, 67, 53), Color.WHITE);

        acceptButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = ACCEPT;
                closeDialog();
            }
        });

        rejectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = REJECT;
                closeDialog();
            }
        });

        buttonPanel.add(rejectButton);
        buttonPanel.add(acceptButton);

        contentPane.add(centerPanel, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                result = REJECT;
                closeDialog();
            }
        });
    }

    private void startBlinkEffect() {
        blinkTimer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isRed = !isRed;
                typeLabel.setForeground(isRed ? Color.RED : Color.BLACK);
            }
        });
        blinkTimer.start();
    }

    private void closeDialog() {
        if (blinkTimer != null && blinkTimer.isRunning()) {
            blinkTimer.stop();
        }
        setVisible(false);
        dispose();
    }

    public int showDialog() {
        setVisible(true);
        return result;
    }
}