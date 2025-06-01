package ui;

import javax.swing.*;
import java.awt.*;

public class AvatarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private String username;
    private Color backgroundColor;

    public AvatarPanel(String username) {
        this.username = username;
        this.backgroundColor = generateColorFromUsername(username);
        setPreferredSize(new Dimension(60, 60));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 4;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;

        g2d.setColor(backgroundColor);
        g2d.fillOval(x, y, size, size);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size / 2));
        FontMetrics fm = g2d.getFontMetrics();
        String text = username.substring(0, 1).toUpperCase();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        g2d.drawString(text,
                x + (size - textWidth) / 2,
                y + (size + textHeight) / 2 - fm.getDescent());

        g2d.dispose();
    }

    private Color generateColorFromUsername(String username) {
        int hash = username.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = hash & 0x0000FF;

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        hsb[1] = Math.max(0.5f, hsb[1]);
        hsb[2] = Math.max(0.6f, hsb[2]);

        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }
}