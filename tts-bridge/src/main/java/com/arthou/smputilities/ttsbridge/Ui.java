package com.arthou.smputilities.ttsbridge;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Small painted widgets shared by the window: rounded cards and pill buttons. */
final class Ui {
    private Ui() {
    }

    /** A rounded, filled surface with a hairline border. */
    static class Card extends JPanel {
        private final Color fill;
        private final Color line;
        private final int radius;

        Card(Color fill, Color line, int radius) {
            this.fill = fill;
            this.line = line;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(line);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Filled amber button, or a quiet outline variant. */
    static class PillButton extends JButton {
        private final boolean primary;
        private boolean hover;

        PillButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(Theme.ui(Font.BOLD, 13));
            setForeground(primary ? new Color(0x1A, 0x14, 0x00) : Theme.ACCENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int r = getHeight();
            if (primary) {
                g2.setColor(hover ? Theme.ACCENT_SOFT : Theme.ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
            } else {
                if (hover) {
                    g2.setColor(Theme.ACCENT_GHOST);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
                }
                g2.setColor(hover ? Theme.ACCENT : Theme.ACCENT_DEEP);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Coloured status dot with a soft halo. */
    static class Dot extends JComponent {
        private Color color = Theme.TEXT_DIM;

        Dot() {
            setPreferredSize(new Dimension(16, 16));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g2.fillOval(0, 0, 16, 16);
            g2.setColor(color);
            g2.fillOval(4, 4, 8, 8);
            g2.dispose();
        }
    }

    /** Left amber rule used to mark section headings. */
    static JComponent heading(String text) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JPanel rule = new JPanel();
        rule.setBackground(Theme.ACCENT);
        rule.setPreferredSize(new Dimension(3, 14));
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.ui(Font.BOLD, 11));
        label.setForeground(Theme.ACCENT);
        row.add(rule, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return row;
    }

    static JLabel body(String text, Color color, int size, int style) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.ui(style, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    static AbstractBorder pad(int t, int l, int b, int r) {
        return (AbstractBorder) BorderFactory.createEmptyBorder(t, l, b, r);
    }
}
