package com.arthou.smputilities.ttsbridge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;

/** The painted widgets of the voice app: cards, buttons, the side navigation, the voice meter and status rows. */
final class Ui {
    private Ui() {
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    private abstract static class Hoverable extends JButton {
        boolean hover;

        Hoverable(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(line);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A card with padding and a vertical stack, the building block of every page. */
    static Card section(String title) {
        Card card = new Card(Theme.SURFACE, Theme.BORDER, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (title != null) {
            JLabel label = body(title, Theme.TEXT, 14, Font.BOLD);
            card.add(label);
            card.add(Box.createVerticalStrut(10));
        }
        return card;
    }

    /** Rounded button: filled teal, a quiet outline, or red for stopping things. */
    static class PillButton extends Hoverable {
        enum Kind { PRIMARY, GHOST, DANGER }

        private final Kind kind;

        PillButton(String text, boolean primary) {
            this(text, primary ? Kind.PRIMARY : Kind.GHOST);
        }

        PillButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(Theme.ui(Font.BOLD, 12));
            setForeground(kind == Kind.PRIMARY ? Theme.ON_ACCENT : kind == Kind.DANGER ? Theme.ERR : Theme.ACCENT);
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int r = 12;
            Color base = kind == Kind.DANGER ? Theme.ERR : Theme.ACCENT;
            if (!isEnabled()) {
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            } else if (kind == Kind.PRIMARY) {
                g2.setColor(hover ? Theme.ACCENT_SOFT : Theme.ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
            } else {
                if (hover) {
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 0x24));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
                }
                g2.setColor(hover ? base : new Color(base.getRed(), base.getGreen(), base.getBlue(), 0x90));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** An entry of the side navigation: a painted icon and a name, with a bar on the current one. */
    static class NavButton extends Hoverable {
        enum Icon { OVERVIEW, MICROPHONE, ACTIVITY }

        private final Icon icon;
        private boolean current;

        NavButton(Icon icon, String text) {
            super(text);
            this.icon = icon;
            setFont(Theme.ui(Font.BOLD, 13));
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(9, 40, 9, 12));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        }

        void setCurrent(boolean value) {
            current = value;
            setForeground(current ? Theme.TEXT : Theme.TEXT_DIM);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (current || hover) {
                g2.setColor(current ? Theme.SURFACE_ALT : Theme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            if (current) {
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
            }
            g2.setColor(current ? Theme.ACCENT : Theme.TEXT_DIM);
            g2.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int x = 14;
            int y = getHeight() / 2 - 7;
            switch (icon) {
                case OVERVIEW -> {
                    // a small level meter
                    g2.fillRoundRect(x, y + 6, 3, 8, 2, 2);
                    g2.fillRoundRect(x + 5, y + 1, 3, 13, 2, 2);
                    g2.fillRoundRect(x + 10, y + 4, 3, 10, 2, 2);
                }
                case MICROPHONE -> {
                    g2.drawRoundRect(x + 4, y, 6, 9, 6, 6);
                    g2.draw(new Arc2D.Float(x + 1, y + 2, 12, 10, 180, 180, Arc2D.OPEN));
                    g2.drawLine(x + 7, y + 12, x + 7, y + 14);
                }
                default -> {
                    g2.drawLine(x + 1, y + 2, x + 13, y + 2);
                    g2.drawLine(x + 1, y + 7, x + 13, y + 7);
                    g2.drawLine(x + 1, y + 12, x + 9, y + 12);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** The app's mark: a microphone dot with sound waves around it. */
    static class Logo extends JComponent {
        Logo(int size) {
            setPreferredSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int s = Math.min(getWidth(), getHeight());
            g2.setPaint(new GradientPaint(0, 0, Theme.ACCENT, s, s, Theme.ACCENT_DEEP));
            g2.fillRoundRect(0, 0, s, s, s / 3, s / 3);
            g2.setColor(Theme.ON_ACCENT);
            g2.setStroke(new BasicStroke(Math.max(1.5F, s / 16F), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float c = s / 2F;
            float dot = s / 7F;
            g2.fill(new java.awt.geom.Ellipse2D.Float(c - s / 4F - dot / 2, c - dot / 2, dot, dot));
            for (int i = 1; i <= 3; i++) {
                float r = i * s / 7F;
                g2.draw(new Arc2D.Float(c - s / 4F - r, c - r, r * 2, r * 2, -45, 90, Arc2D.OPEN));
            }
            g2.dispose();
        }
    }

    /** Coloured status dot with a soft halo. */
    static class Dot extends JComponent {
        private Color color = Theme.TEXT_DIM;

        Dot() {
            setPreferredSize(new Dimension(14, 14));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g2.fillOval(0, 0, 14, 14);
            g2.setColor(color);
            g2.fillOval(4, 4, 6, 6);
            g2.dispose();
        }
    }

    /**
     * Bars that move like a voice while something is being spoken and
     * settle into a slow ripple when it is quiet.
     */
    static class VoiceMeter extends JComponent {
        private static final int BARS = 40;
        private long activeUntil;
        private final long start = System.currentTimeMillis();

        VoiceMeter() {
            setPreferredSize(new Dimension(400, 84));
            setMinimumSize(new Dimension(200, 60));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            new Timer(40, e -> repaint()).start();
        }

        void pulse() {
            activeUntil = System.currentTimeMillis() + 2200;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            long now = System.currentTimeMillis();
            double time = (now - start) / 1000.0;
            float energy = (float) Math.max(0, Math.min(1, (activeUntil - now) / 600.0));
            int gap = 4;
            float barWidth = Math.max(2F, (getWidth() - gap * (BARS - 1F)) / BARS);
            int middle = getHeight() / 2;
            for (int i = 0; i < BARS; i++) {
                double idle = 0.08 + 0.05 * Math.sin(time * 1.6 + i * 0.35);
                double voice = Math.abs(Math.sin(time * 7.3 + i * 0.9)) * 0.55 + Math.abs(Math.sin(time * 3.1 + i * 0.37)) * 0.45;
                double level = idle + (voice - idle) * energy;
                int half = (int) Math.max(2, level * (getHeight() / 2.0 - 2));
                float x = i * (barWidth + gap);
                float mix = (float) i / (BARS - 1);
                Color color = blend(Theme.ACCENT, Theme.ACCENT_DEEP, mix);
                g2.setColor(energy > 0 ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), 110));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(x, middle - half, barWidth, half * 2F, barWidth, barWidth));
            }
            g2.dispose();
        }

        private static Color blend(Color a, Color b, float t) {
            return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }

    /** A small figure with its caption, for the overview's row of numbers. */
    static Card tile(String caption, JLabel value) {
        Card card = new Card(Theme.SURFACE, Theme.BORDER, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        value.setFont(Theme.ui(Font.BOLD, 20));
        value.setForeground(Theme.TEXT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(value);
        card.add(Box.createVerticalStrut(2));
        card.add(body(caption, Theme.TEXT_DIM, 11, Font.PLAIN));
        return card;
    }

    /** One line of the "ready to play" checklist: a check or a warning, what it is, and how it stands. */
    static class CheckRow extends JPanel {
        private final Mark mark = new Mark();
        private final JLabel detail = body(" ", Theme.TEXT_DIM, 11, Font.PLAIN);

        CheckRow(String title) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            JPanel markHolder = new JPanel(new BorderLayout());
            markHolder.setOpaque(false);
            markHolder.add(mark, BorderLayout.NORTH);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(body(title, Theme.TEXT, 12, Font.BOLD));
            text.add(detail);
            add(markHolder, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            set(false, " ");
        }

        void set(boolean ok, String text) {
            mark.ok = ok;
            mark.repaint();
            detail.setText(text);
        }

        /** A green circle with a tick, or an amber one with an exclamation mark. */
        private static final class Mark extends JComponent {
            boolean ok;

            Mark() {
                setPreferredSize(new Dimension(18, 18));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                Color color = ok ? Theme.OK : Theme.WARN;
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 0x33));
                g2.fillOval(0, 0, 17, 17);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (ok) {
                    g2.drawPolyline(new int[] {5, 8, 13}, new int[] {9, 12, 6}, 3);
                } else {
                    g2.drawLine(9, 4, 9, 10);
                    g2.fillOval(8, 12, 3, 3);
                }
                g2.dispose();
            }
        }
    }

    /** A dark drop-down with a teal arrow, matching the cards. */
    static void styleCombo(JComboBox<?> box) {
        box.setBackground(Theme.SURFACE_ALT);
        box.setForeground(Theme.TEXT);
        box.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        box.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton arrow = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.SOUTH, Theme.SURFACE_ALT, Theme.SURFACE_ALT,
                    Theme.ACCENT, Theme.SURFACE_ALT);
                arrow.setBorder(BorderFactory.createEmptyBorder());
                return arrow;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(Theme.SURFACE_ALT);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setBackground(selected && index >= 0 ? Theme.ACCENT_DEEP : Theme.SURFACE_ALT);
                setForeground(Theme.TEXT);
                setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                list.setBackground(Theme.SURFACE_ALT);
                return this;
            }
        });
    }

    /** A thin rounded scroll bar in the window's colors. */
    static void styleScrollBar(JScrollBar bar) {
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(10, 10));
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = Theme.BORDER;
                trackColor = Theme.BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return invisible();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return invisible();
            }

            private JButton invisible() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
                Graphics2D g2 = smooth(g);
                g2.setColor(isDragging ? Theme.ACCENT_DEEP : thumbColor);
                g2.fillRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 6, 6);
                g2.dispose();
            }
        });
    }

    static JLabel body(String text, Color color, int size, int style) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.ui(style, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** A teal label that opens a web page. */
    static JLabel link(String text, String url) {
        JLabel label = body(text, Theme.ACCENT, 12, Font.BOLD);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create(url));
                } catch (Exception ignored) {
                    // no browser: the address is visible anyway
                }
            }
        });
        return label;
    }

    /** Buttons in a row, left aligned. */
    static JPanel row(JComponent... items) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                row.add(Box.createHorizontalStrut(8));
            }
            row.add(items[i]);
        }
        row.add(Box.createHorizontalGlue());
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }
}
