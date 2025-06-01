package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private BufferedImage image;
    private boolean keepAspectRatio = true;

    public VideoPanel() {
        setBackground(Color.BLACK);
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    public void setKeepAspectRatio(boolean keep) {
        this.keepAspectRatio = keep;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image != null) {
            Graphics2D g2d = (Graphics2D) g;

            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (keepAspectRatio) {
                double imageRatio = (double) image.getWidth() / image.getHeight();
                double panelRatio = (double) getWidth() / getHeight();

                int drawWidth, drawHeight, x, y;

                if (imageRatio > panelRatio) {
                    drawWidth = getWidth();
                    drawHeight = (int) (drawWidth / imageRatio);
                    x = 0;
                    y = (getHeight() - drawHeight) / 2;
                } else {
                    drawHeight = getHeight();
                    drawWidth = (int) (drawHeight * imageRatio);
                    x = (getWidth() - drawWidth) / 2;
                    y = 0;
                }

                g2d.drawImage(image, x, y, drawWidth, drawHeight, null);
            } else {
                g2d.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }
}
