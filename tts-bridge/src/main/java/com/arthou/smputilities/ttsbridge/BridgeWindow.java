package com.arthou.smputilities.ttsbridge;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The bridge control window: status, connection details, instructions and a live log. */
final class BridgeWindow extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Ui.Dot dot = new Ui.Dot();
    private final JLabel stateLabel = new JLabel("Starting…");
    private final JLabel servedLabel = new JLabel("0");
    private final JTextArea log = new JTextArea();
    private final Runnable onStop;

    BridgeWindow(int port, Runnable onStop) {
        super("SMPutilities Voice Studio");
        this.onStop = onStop;

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(660, 640);
        setMinimumSize(new Dimension(560, 560));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);

        JPanel status = new JPanel();
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        status.setBackground(Theme.BG);
        status.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        status.add(statusCard(port));
        status.add(Box.createVerticalStrut(14));
        status.add(instructionsCard());
        status.add(Box.createVerticalStrut(14));
        status.add(logCard());
        status.add(Box.createVerticalStrut(16));
        status.add(buttonRow(port));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(Theme.SURFACE);
        tabs.setForeground(Theme.TEXT);
        tabs.setFont(Theme.ui(Font.BOLD, 12));
        tabs.addTab("Status", scroll(status));
        tabs.addTab("Audio & Microphone", scroll(audioPanel()));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        root.add(header(), BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JComponent header() {
        JPanel panel = new JPanel(new BorderLayout(14, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

        JPanel bar = new JPanel();
        bar.setBackground(Theme.ACCENT);
        bar.setPreferredSize(new Dimension(5, 52));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel title = new JLabel("SMPutilities Voice Studio");
        title.setFont(Theme.ui(Font.BOLD, 24));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = Ui.body("Local text-to-speech service for the SMPutilities mod",
            Theme.TEXT_DIM, 12, Font.PLAIN);
        text.add(title);
        text.add(Box.createVerticalStrut(3));
        text.add(sub);

        panel.add(bar, BorderLayout.WEST);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private JComponent statusCard(int port) {
        Ui.Card card = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        stateLabel.setFont(Theme.ui(Font.BOLD, 17));
        stateLabel.setForeground(Theme.TEXT);
        top.add(dot);
        top.add(stateLabel);
        card.add(top);
        card.add(Box.createVerticalStrut(12));

        int cloudVoices = RemoteTts.voiceCount();
        boolean cloudOk = cloudVoices > 0;
        String player = Player.backend();
        card.add(detailRow("Endpoint", "http://127.0.0.1:" + port));
        card.add(detailRow("Platform", Theme.osLabel()));
        card.add(detailRow("Voice source",
            cloudOk ? "ttstool.com  (" + cloudVoices + " voices)" : "ttstool.com unreachable",
            cloudOk ? Theme.OK : Theme.ERR));
        card.add(detailRow("Audio player", "none".equals(player) ? "not found" : player,
            "none".equals(player) ? Theme.ERR : Theme.TEXT));
        card.add(detailRow("Offline fallback", Tts.engine().label,
            Tts.engine() == Tts.Engine.NONE ? Theme.TEXT_DIM : Theme.TEXT));

        JPanel served = new JPanel(new BorderLayout());
        served.setOpaque(false);
        served.setAlignmentX(Component.LEFT_ALIGNMENT);
        served.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel k = Ui.body("Phrases spoken", Theme.TEXT_DIM, 12, Font.PLAIN);
        k.setPreferredSize(new Dimension(130, 20));
        servedLabel.setFont(Theme.mono(Font.BOLD, 12));
        servedLabel.setForeground(Theme.ACCENT);
        served.add(k, BorderLayout.WEST);
        served.add(servedLabel, BorderLayout.CENTER);
        card.add(served);

        if (!cloudOk && !Tts.installHint().isBlank()) {
            card.add(Box.createVerticalStrut(8));
            JLabel hint = Ui.body(Tts.installHint(), Theme.ERR, 12, Font.BOLD);
            card.add(hint);
        }
        return card;
    }

    private JComponent detailRow(String key, String value) {
        return detailRow(key, value, Theme.TEXT);
    }

    private JComponent detailRow(String key, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel k = Ui.body(key, Theme.TEXT_DIM, 12, Font.PLAIN);
        k.setPreferredSize(new Dimension(130, 20));
        JLabel v = new JLabel(value);
        v.setFont(Theme.mono(Font.PLAIN, 12));
        v.setForeground(valueColor);
        row.add(k, BorderLayout.WEST);
        row.add(v, BorderLayout.CENTER);
        return row;
    }

    private JComponent instructionsCard() {
        Ui.Card card = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));

        card.add(Ui.heading("How to use"));
        card.add(Box.createVerticalStrut(10));

        String[] steps = {
            "Leave this window open (or hidden) while you play. Closing it only hides it.",
            "The bridge starts automatically with Minecraft — you never launch it by hand.",
            "In game, text-to-speech messages are sent here and spoken by your system voice.",
            "Use “Test voice” below to confirm your speakers and speech engine work.",
            "Quitting Minecraft stops the bridge automatically."
        };
        for (int i = 0; i < steps.length; i++) {
            card.add(step(i + 1, steps[i]));
            card.add(Box.createVerticalStrut(6));
        }
        return card;
    }

    private JComponent step(int number, String text) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel index = new JLabel(String.valueOf(number));
        index.setFont(Theme.mono(Font.BOLD, 12));
        index.setForeground(Theme.ACCENT);
        index.setPreferredSize(new Dimension(16, 18));
        index.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel label = Ui.body(text, Theme.TEXT, 12, Font.PLAIN);
        row.add(index, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private JComponent logCard() {
        Ui.Card card = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(Integer.MAX_VALUE, 168));

        card.add(Ui.heading("Activity"));
        card.add(Box.createVerticalStrut(10));

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(Theme.mono(Font.PLAIN, 11));
        log.setForeground(Theme.TEXT_DIM);
        log.setBackground(Theme.SURFACE_ALT);
        log.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.SURFACE_ALT);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(scroll);
        return card;
    }

    private JComponent buttonRow(int port) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        Ui.PillButton test = new Ui.PillButton("Test voice", true);
        test.addActionListener(e -> {
            try {
                Tts.speak("SMPutilities voice bridge is working.", "Amazon US English (Ivy)", "default");
                append("Test phrase sent via " + Tts.lastSource());
            } catch (Exception exception) {
                append("Test failed: " + exception.getMessage());
            }
        });

        Ui.PillButton copy = new Ui.PillButton("Copy endpoint", false);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection("http://127.0.0.1:" + port), null);
            append("Endpoint copied to clipboard");
        });

        Ui.PillButton hide = new Ui.PillButton("Hide window", false);
        hide.addActionListener(e -> setVisible(false));

        Ui.PillButton stop = new Ui.PillButton("Stop bridge", false);
        stop.setForeground(Theme.ERR);
        stop.addActionListener(e -> onStop.run());

        row.add(test);
        row.add(copy);
        row.add(hide);
        row.add(stop);
        return row;
    }

    private static JScrollPane scroll(JComponent content) {
        JScrollPane pane = new JScrollPane(content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(null);
        pane.getViewport().setBackground(Theme.BG);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    /** Lets the user send the synthesized voice into a virtual cable, so the
     *  game picks it up as a microphone (the Linux answer to VB-Cable). */
    private JComponent audioPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        if (!Audio.available()) {
            Ui.Card card = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(Ui.heading("Not available"));
            card.add(Box.createVerticalStrut(10));
            card.add(Ui.body(Tts.WINDOWS
                ? "On Windows, install VB-Cable and pick \"CABLE Input\" as the voice output below."
                : "PulseAudio / PipeWire tools were not found on this system.",
                Theme.TEXT, 12, Font.PLAIN));
            panel.add(card);
            return panel;
        }

        // --- virtual cable (Linux) or VB-Cable guidance (Windows) ---
        if (!Audio.canCreateCable()) {
            Ui.Card vb = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
            vb.setLayout(new BoxLayout(vb, BoxLayout.Y_AXIS));
            vb.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            vb.setAlignmentX(Component.LEFT_ALIGNMENT);
            vb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            vb.add(Ui.heading("Virtual cable"));
            vb.add(Box.createVerticalStrut(10));
            vb.add(Ui.body("Windows needs a driver to create a virtual microphone, so this",
                Theme.TEXT_DIM, 12, Font.PLAIN));
            vb.add(Ui.body("app cannot make one. Install VB-Cable once, then pick",
                Theme.TEXT_DIM, 12, Font.PLAIN));
            vb.add(Ui.body("\"CABLE Input\" below as the voice output.", Theme.TEXT_DIM, 12, Font.PLAIN));
            vb.add(Box.createVerticalStrut(8));
            JLabel link = Ui.body("vb-audio.com/Cable", Theme.ACCENT, 12, Font.BOLD);
            vb.add(link);
            panel.add(vb);
            panel.add(Box.createVerticalStrut(14));
        }

        Ui.Card cableCard = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        cableCard.setLayout(new BoxLayout(cableCard, BoxLayout.Y_AXIS));
        cableCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        cableCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        cableCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        cableCard.add(Ui.heading("Virtual cable"));
        cableCard.add(Box.createVerticalStrut(10));
        cableCard.add(Ui.body("Creates a virtual audio device. The voice is played into it,",
            Theme.TEXT_DIM, 12, Font.PLAIN));
        cableCard.add(Ui.body("and the game hears it as a microphone.", Theme.TEXT_DIM, 12, Font.PLAIN));
        cableCard.add(Box.createVerticalStrut(10));

        JLabel cableState = new JLabel();
        cableState.setFont(Theme.mono(Font.BOLD, 12));
        cableState.setAlignmentX(Component.LEFT_ALIGNMENT);
        cableCard.add(cableState);
        cableCard.add(Box.createVerticalStrut(12));

        JPanel cableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cableButtons.setOpaque(false);
        cableButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        Ui.PillButton create = new Ui.PillButton("Create cable", true);
        Ui.PillButton remove = new Ui.PillButton("Remove cable", false);
        cableButtons.add(create);
        cableButtons.add(remove);
        cableCard.add(cableButtons);
        if (Audio.canCreateCable()) {
            panel.add(cableCard);
            panel.add(Box.createVerticalStrut(14));
        }

        // --- output device ---
        Ui.Card deviceCard = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        deviceCard.setLayout(new BoxLayout(deviceCard, BoxLayout.Y_AXIS));
        deviceCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        deviceCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        deviceCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        deviceCard.add(Ui.heading("Where the voice is played"));
        deviceCard.add(Box.createVerticalStrut(10));

        JComboBox<Audio.Device> devices = new JComboBox<>();
        devices.setFont(Theme.ui(Font.PLAIN, 12));
        devices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        devices.setAlignmentX(Component.LEFT_ALIGNMENT);
        deviceCard.add(devices);
        deviceCard.add(Box.createVerticalStrut(10));
        JLabel saved = Ui.body(" ", Theme.OK, 11, Font.PLAIN);
        deviceCard.add(saved);
        panel.add(deviceCard);
        panel.add(Box.createVerticalStrut(14));

        // --- hear it yourself ---
        Ui.Card monitorCard = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        monitorCard.setLayout(new BoxLayout(monitorCard, BoxLayout.Y_AXIS));
        monitorCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        monitorCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        monitorCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        monitorCard.add(Ui.heading("Hear it yourself"));
        monitorCard.add(Box.createVerticalStrut(10));
        monitorCard.add(Ui.body("Also play the voice on your headphones, while the game",
            Theme.TEXT_DIM, 12, Font.PLAIN));
        monitorCard.add(Ui.body("keeps receiving it as a microphone.", Theme.TEXT_DIM, 12, Font.PLAIN));
        monitorCard.add(Box.createVerticalStrut(10));

        JComboBox<Audio.Device> monitors = new JComboBox<>();
        monitors.setFont(Theme.ui(Font.PLAIN, 12));
        monitors.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        monitors.setAlignmentX(Component.LEFT_ALIGNMENT);
        monitorCard.add(monitors);
        monitorCard.add(Box.createVerticalStrut(8));
        JLabel monitorState = Ui.body(" ", Theme.OK, 11, Font.PLAIN);
        monitorCard.add(monitorState);
        panel.add(monitorCard);
        panel.add(Box.createVerticalStrut(14));

        // --- what to pick in game ---
        Ui.Card gameCard = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        gameCard.setLayout(new BoxLayout(gameCard, BoxLayout.Y_AXIS));
        gameCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        gameCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        gameCard.add(Ui.heading("In the game"));
        gameCard.add(Box.createVerticalStrut(10));
        gameCard.add(Ui.body("Open your voice chat settings and choose this microphone:",
            Theme.TEXT_DIM, 12, Font.PLAIN));
        gameCard.add(Box.createVerticalStrut(6));
        JLabel monitor = new JLabel(Audio.canCreateCable() ? Audio.MIC_DESCRIPTION : "CABLE Output (VB-Audio Virtual Cable)");
        monitor.setFont(Theme.mono(Font.BOLD, 12));
        monitor.setForeground(Theme.ACCENT);
        monitor.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameCard.add(monitor);
        gameCard.add(Box.createVerticalStrut(10));
        JPanel gameButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        gameButtons.setOpaque(false);
        gameButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        Ui.PillButton copyMonitor = new Ui.PillButton("Copy name", false);
        copyMonitor.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(monitor.getText()), null));
        Ui.PillButton refresh = new Ui.PillButton("Refresh", false);
        gameButtons.add(copyMonitor);
        gameButtons.add(refresh);
        gameCard.add(gameButtons);
        panel.add(gameCard);

        boolean[] populating = {false};
        Runnable reload = () -> {
            boolean exists = Audio.cableExists() && (!Audio.canCreateCable() || Audio.micExists());
            cableState.setText(exists ? "Cable is active" : "Cable not created yet");
            cableState.setForeground(exists ? Theme.OK : Theme.TEXT_DIM);
            create.setEnabled(!exists);
            remove.setEnabled(exists);

            // Populating fires the action listener; without this guard the first
            // added item would overwrite the saved choice with "system default".
            populating[0] = true;
            try {
                devices.removeAllItems();
                devices.addItem(new Audio.Device("", "System default device"));
                String current = Config.outputSink();
                Audio.Device match = null;
                for (Audio.Device device : Audio.sinks()) {
                    devices.addItem(device);
                    if (device.name().equals(current)) {
                        match = device;
                    }
                    // With no choice yet, preselect the cable: it is the whole point.
                    if (match == null && current.isBlank() && device.name().equals(Audio.CABLE_SINK)) {
                        match = device;
                    }
                }
                if (match != null) {
                    devices.setSelectedItem(match);
                    if (current.isBlank()) {
                        Config.setOutputSink(match.name());
                    }
                    saved.setText("Saved: " + match);
                }
                monitors.removeAllItems();
                monitors.addItem(new Audio.Device("", "Off  (only the game hears it)"));
                String activeMonitor = Audio.canCreateCable() ? Audio.loopbackDestination() : Config.monitorSink();
                if (activeMonitor == null) {
                    activeMonitor = "";
                }
                Audio.Device monitorMatch = null;
                for (Audio.Device device : Audio.sinks()) {
                    if (device.name().equals(Audio.CABLE_SINK)) {
                        continue;  // looping the cable into itself would feed back
                    }
                    monitors.addItem(device);
                    if (device.name().equals(activeMonitor)) {
                        monitorMatch = device;
                    }
                }
                if (monitorMatch != null) {
                    monitors.setSelectedItem(monitorMatch);
                    monitorState.setText("Playing on: " + monitorMatch);
                    monitorState.setForeground(Theme.OK);
                } else {
                    monitorState.setText("Not playing on your speakers");
                    monitorState.setForeground(Theme.TEXT_DIM);
                }
            } finally {
                populating[0] = false;
            }
        };

        devices.addActionListener(e -> {
            if (populating[0]) {
                return;
            }
            Audio.Device selected = (Audio.Device) devices.getSelectedItem();
            if (selected == null) {
                return;
            }
            Config.setOutputSink(selected.name());
            saved.setText("Saved: " + selected);
            append("Voice output set to " + selected);
        });

        monitors.addActionListener(e -> {
            if (populating[0]) {
                return;
            }
            Audio.Device selected = (Audio.Device) monitors.getSelectedItem();
            if (selected == null) {
                return;
            }
            Config.setMonitorSink(selected.name());
            if (!Audio.canCreateCable()) {
                // Windows plays on both devices directly, no loopback needed.
                boolean off = selected.name().isBlank();
                monitorState.setText(off ? "Not playing on your speakers" : "Playing on: " + selected);
                monitorState.setForeground(off ? Theme.TEXT_DIM : Theme.OK);
                append(off ? "Monitoring off" : "Also playing the voice on " + selected);
                return;
            }
            if (selected.name().isBlank()) {
                Audio.removeLoopback();
                monitorState.setText("Not playing on your speakers");
                monitorState.setForeground(Theme.TEXT_DIM);
                append("Monitoring off");
            } else {
                String error = Audio.createLoopback(selected.name());
                if (error == null) {
                    monitorState.setText("Playing on: " + selected);
                    monitorState.setForeground(Theme.OK);
                    append("Also playing the voice on " + selected);
                } else {
                    monitorState.setText("Failed: " + error);
                    monitorState.setForeground(Theme.ERR);
                    append("Could not start monitoring: " + error);
                }
            }
        });

        create.addActionListener(e -> {
            String error = Audio.createCable();
            append(error == null ? "Virtual cable created" : "Could not create cable: " + error);
            reload.run();
        });
        remove.addActionListener(e -> {
            Audio.removeLoopback();
            Audio.removeCable();
            append("Virtual cable removed");
            reload.run();
        });
        refresh.addActionListener(e -> reload.run());

        reload.run();
        return panel;
    }

    void setRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            dot.setColor(running ? Theme.OK : Theme.ERR);
            stateLabel.setText(running ? "Running" : "Stopped");
        });
    }

    void setServed(int count) {
        SwingUtilities.invokeLater(() -> servedLabel.setText(String.valueOf(count)));
    }

    void append(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append("[" + LocalTime.now().format(CLOCK) + "]  " + line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }
}
