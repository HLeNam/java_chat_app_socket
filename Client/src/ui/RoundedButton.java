package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class RoundedButton extends JButton {
    private static final long serialVersionUID = 1L;
    private Color backgroundColor;
    private Color foregroundColor;
    private int arcWidth = 15;
    private int arcHeight = 15;

    public RoundedButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);

        backgroundColor = new Color(59, 89, 152); // Facebook blue
        foregroundColor = Color.WHITE;
    }

    public RoundedButton(String text, Color bgColor, Color fgColor) {
        this(text);
        this.backgroundColor = bgColor;
        this.foregroundColor = fgColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (getModel().isPressed()) {
            g2d.setColor(backgroundColor.darker());
        } else if (getModel().isRollover()) {
            g2d.setColor(backgroundColor.brighter());
        } else {
            g2d.setColor(backgroundColor);
        }

        g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arcWidth, arcHeight));

        FontMetrics metrics = g2d.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(getText())) / 2;
        int y = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();

        g2d.setColor(foregroundColor);
        g2d.drawString(getText(), x, y);
        g2d.dispose();

        super.paintComponent(g);
    }
}