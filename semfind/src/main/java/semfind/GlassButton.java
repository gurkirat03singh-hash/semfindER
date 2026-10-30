package semfind;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.Font;
import javax.swing.BorderFactory;


public class GlassButton extends JButton {

    private boolean hover = false;

    public GlassButton(String text) {

        super(text);
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        setBorder(BorderFactory.createEmptyBorder(9, 15, 9, 15));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {

            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(
    RenderingHints.KEY_ANTIALIASING,
    RenderingHints.VALUE_ANTIALIAS_ON
);
        if (hover) {
            g2.setColor(new Color(0,120,255,80));
        } else {
            g2.setColor(new Color(255,255,255,60));
        }

        g2.fillRoundRect(
            0,0,
            getWidth(),
            getHeight(),
            20,20

        );

        g2.dispose();
        super.paintComponent(g);
    }
}