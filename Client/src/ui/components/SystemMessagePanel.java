package ui.components;

import javax.swing.*;
import java.awt.*;

public class SystemMessagePanel extends JPanel {
    public SystemMessagePanel(String message) {
        setLayout(new BorderLayout());
        setBackground(new Color(245, 245, 245));
        setBorder(BorderFactory.createEmptyBorder(8, 5, 8, 5));

        JLabel label = new JLabel("--- " + message + " ---", SwingConstants.CENTER);
        label.setForeground(Color.GRAY);
        label.setFont(new Font(label.getFont().getName(), Font.ITALIC, 12));

        add(label, BorderLayout.CENTER);

        // Đảm bảo hiển thị đúng trong BoxLayout
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }
}