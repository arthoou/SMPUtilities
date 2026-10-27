package com.arthou.smputilities.ttsbridge;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.arthou.smputilities.ttsbridge.I18n.t;

/**
 * The voice app's window: a side bar with the status and three pages. The
 * overview shows a live voice meter, the numbers and a "ready to play"
 * checklist with a test field; the microphone page walks through the virtual
 * microphone setup step by step; the activity page keeps the log.
 */
final class BridgeWindow extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int LOG_LIMIT = 500;

    private final int port;
    private final Runnable onStop;
    private final Ui.Dot dot = new Ui.Dot();
    private final JLabel stateLabel = Ui.body(t("Starting…", "Iniciando…"), Theme.TEXT, 12, Font.BOLD);
    private final JLabel servedValue = new JLabel("0");
    private final JLabel lastLine = Ui.body(t("Nothing spoken yet.", "Nada falado ainda."), Theme.TEXT_DIM, 12, Font.PLAIN);
    private final Ui.VoiceMeter meter = new Ui.VoiceMeter();
    private final DefaultListModel<String> logModel = new DefaultListModel<>();
    private final JList<String> logList = new JList<>(logModel);
    private final CardLayout pages = new CardLayout();
    private final JPanel pageHost = new JPanel(pages);
    private final List<Ui.NavButton> nav = new ArrayList<>();
    private final Ui.CheckRow checkBridge = new Ui.CheckRow(t("Voice app", "App de voz"));
    private final Ui.CheckRow checkVoices = new Ui.CheckRow(t("Voices", "Vozes"));
    private final Ui.CheckRow checkMic = new Ui.CheckRow(t("Virtual microphone", "Microfone virtual"));
    private final Ui.CheckRow checkOutput = new Ui.CheckRow(t("Voice output", "Saída da voz"));
    private boolean running;

    BridgeWindow(int port, Runnable onStop) {
        super("SMPutilities Voice");
        this.port = port;
        this.onStop = onStop;
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(800, 560);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(null);

        pageHost.setBackground(Theme.BG);
        pageHost.add(page(overviewPage()), "overview");
        pageHost.add(page(microphonePage()), "microphone");
        pageHost.add(activityPage(), "activity");

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(pageHost, BorderLayout.CENTER);
        setContentPane(root);
        show(0);
        refreshChecks();
    }

    // ------------------------------------------------------------- side bar

    private JComponent sidebar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(Theme.SIDEBAR);
        bar.setBorder(BorderFactory.createEmptyBorder(18, 14, 16, 14));
        bar.setPreferredSize(new Dimension(210, 0));

        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.X_AXIS));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(new Ui.Logo(36));
        brand.add(Box.createHorizontalStrut(10));
        JPanel names = new JPanel();
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.setOpaque(false);
        names.add(Ui.body("SMPutilities", Theme.TEXT, 14, Font.BOLD));
        names.add(Ui.body(t("Voice", "Voz"), Theme.ACCENT, 12, Font.BOLD));
        brand.add(names);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bar.add(brand);
        bar.add(Box.createVerticalStrut(16));

        Ui.Card status = new Ui.Card(Theme.SURFACE, Theme.BORDER, 14);
        status.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        status.setBorder(BorderFactory.createEmptyBorder(9, 2, 7, 10));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        status.add(dot);
        status.add(stateLabel);
        bar.add(status);
        bar.add(Box.createVerticalStrut(18));

        String[] items = {t("Overview", "Visão geral"), t("Microphone", "Microfone"), t("Activity", "Atividade")};
        for (int i = 0; i < items.length; i++) {
            int index = i;
            Ui.NavButton button = new Ui.NavButton(Ui.NavButton.Icon.values()[i], items[i]);
            button.addActionListener(e -> show(index));
            nav.add(button);
            bar.add(button);
            bar.add(Box.createVerticalStrut(4));
        }

        bar.add(Box.createVerticalGlue());
        JLabel endpoint = Ui.body("127.0.0.1:" + port, Theme.TEXT_DIM, 11, Font.PLAIN);
        endpoint.setFont(Theme.mono(Font.PLAIN, 11));
        bar.add(endpoint);
        bar.add(Box.createVerticalStrut(10));
        Ui.PillButton hide = new Ui.PillButton(t("Hide", "Ocultar"), Ui.PillButton.Kind.GHOST);
        hide.addActionListener(e -> setVisible(false));
        Ui.PillButton stop = new Ui.PillButton(t("Stop", "Parar"), Ui.PillButton.Kind.DANGER);
        stop.addActionListener(e -> onStop.run());
        bar.add(Ui.row(hide, stop));
        return bar;
    }

    private void show(int index) {
        pages.show(pageHost, new String[] {"overview", "microphone", "activity"}[index]);
        for (int i = 0; i < nav.size(); i++) {
            nav.get(i).setCurrent(i == index);
        }
    }

    private static JComponent page(JComponent content) {
        JScrollPane pane = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(null);
        pane.getViewport().setBackground(Theme.BG);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        Ui.styleScrollBar(pane.getVerticalScrollBar());
        return pane;
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        return panel;
    }

    private static void title(JPanel page, String title, String subtitle) {
        page.add(Ui.body(title, Theme.TEXT, 22, Font.BOLD));
        page.add(Box.createVerticalStrut(4));
        page.add(Ui.body(subtitle, Theme.TEXT_DIM, 12, Font.PLAIN));
        page.add(Box.createVerticalStrut(18));
    }

    // ------------------------------------------------------------- overview

    private JComponent overviewPage() {
        JPanel page = column();
        title(page, t("Overview", "Visão geral"),
            t("Speaks what you type in Minecraft into your microphone.", "Fala no seu microfone o que você digita no Minecraft."));

        Ui.Card live = Ui.section(null);
        live.add(meter);
        live.add(Box.createVerticalStrut(8));
        live.add(lastLine);
        page.add(live);
        page.add(Box.createVerticalStrut(12));

        int voices = RemoteTts.voiceCount();
        String player = Player.backend();
        JPanel tiles = new JPanel(new GridLayout(1, 3, 10, 0));
        tiles.setOpaque(false);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        tiles.add(Ui.tile(t("phrases spoken", "falas"), servedValue));
        tiles.add(Ui.tile(t("cloud voices", "vozes na nuvem"), new JLabel(voices > 0 ? String.valueOf(voices) : t("offline", "offline"))));
        tiles.add(Ui.tile(t("audio player", "reprodutor"), new JLabel("none".equals(player) ? t("missing", "faltando") : player)));
        page.add(tiles);
        page.add(Box.createVerticalStrut(12));

        Ui.Card checks = Ui.section(t("Ready to play", "Pronto para jogar"));
        checks.add(checkBridge);
        checks.add(Box.createVerticalStrut(6));
        checks.add(checkVoices);
        checks.add(Box.createVerticalStrut(6));
        checks.add(checkMic);
        checks.add(Box.createVerticalStrut(6));
        checks.add(checkOutput);
        checks.add(Box.createVerticalStrut(10));
        Ui.PillButton setup = new Ui.PillButton(t("Set up the microphone", "Configurar o microfone"), false);
        setup.addActionListener(e -> show(1));
        checks.add(Ui.row(setup));
        page.add(checks);
        page.add(Box.createVerticalStrut(12));

        Ui.Card test = Ui.section(t("Try it", "Testar"));
        JTextField sample = new JTextField(t("SMPutilities voice is working.", "A voz do SMPutilities está funcionando."));
        sample.setFont(Theme.ui(Font.PLAIN, 13));
        sample.setForeground(Theme.TEXT);
        sample.setCaretColor(Theme.ACCENT);
        sample.setBackground(Theme.SURFACE_ALT);
        sample.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        sample.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        sample.setAlignmentX(Component.LEFT_ALIGNMENT);
        Ui.PillButton speak = new Ui.PillButton(t("Speak", "Falar"), true);
        speak.addActionListener(e -> {
            String text = sample.getText().isBlank() ? "SMPutilities" : sample.getText();
            meter.pulse();
            new Thread(() -> {
                try {
                    Tts.speak(text, "Amazon US English (Ivy)", "default");
                    append(t("Test spoken via ", "Teste falado via ") + Tts.lastSource());
                } catch (Exception exception) {
                    append(t("Test failed: ", "O teste falhou: ") + exception.getMessage());
                }
            }, "smputilities-voice-test").start();
        });
        test.add(sample);
        test.add(Box.createVerticalStrut(10));
        test.add(Ui.row(speak));
        page.add(test);
        return page;
    }

    /** Re-reads what the checklist shows; cheap enough to call after every audio change. */
    private void refreshChecks() {
        checkBridge.set(running, running ? t("Listening for the game on ", "Esperando o jogo em ") + "127.0.0.1:" + port
            : t("Not running", "Parado"));
        int voices = RemoteTts.voiceCount();
        checkVoices.set(voices > 0 || Tts.engine() != Tts.Engine.NONE, voices > 0
            ? "ttstool.com · " + voices + t(" voices", " vozes")
            : t("Cloud voices unreachable; offline: ", "Vozes na nuvem fora do ar; offline: ") + Tts.engine().label);
        if (Audio.canCreateCable()) {
            boolean mic = Audio.cableExists() && Audio.micExists();
            checkMic.set(mic, mic ? Audio.MIC_DESCRIPTION : t("Not created yet: open Microphone", "Ainda não criado: abra Microfone"));
        } else if (Audio.usesDriverCable()) {
            boolean cable = Audio.cableExists();
            checkMic.set(cable, cable ? Audio.driverName()
                : t("Install ", "Instale o ") + Audio.driverName() + " (" + Audio.driverLink() + ")");
        } else {
            checkMic.set(false, t("Not available on this system", "Não disponível neste sistema"));
        }
        String output = Config.outputSink();
        boolean toCable = Audio.isCable(output);
        checkOutput.set(toCable, toCable ? t("Into the virtual microphone", "No microfone virtual")
            : t("Plays on your speakers: pick the cable", "Toca nos alto-falantes: escolha o cabo"));
    }

    // ----------------------------------------------------------- microphone

    private JComponent microphonePage() {
        JPanel page = column();
        title(page, t("Microphone", "Microfone"),
            t("Four steps so the game hears the voice as your microphone.", "Quatro passos para o jogo ouvir a voz como o seu microfone."));

        if (!Audio.available()) {
            Ui.Card card = Ui.section(t("Not available", "Não disponível"));
            card.add(Ui.body(Audio.usesDriverCable()
                ? t("Install " + Audio.driverName() + ", then pick \"" + Audio.driverOutput() + "\" as the voice output here.",
                    "Instale o " + Audio.driverName() + " e escolha \"" + Audio.driverOutput() + "\" como saída da voz aqui.")
                : t("PulseAudio / PipeWire tools were not found on this system.", "As ferramentas do PulseAudio / PipeWire não foram encontradas."),
                Theme.TEXT, 12, Font.PLAIN));
            page.add(card);
            return page;
        }

        // 1. the virtual microphone
        Ui.Card first = step(1, t("Create the virtual microphone", "Crie o microfone virtual"));
        JLabel cableState = Ui.body(" ", Theme.TEXT_DIM, 12, Font.BOLD);
        Ui.PillButton create = new Ui.PillButton(t("Create", "Criar"), true);
        Ui.PillButton remove = new Ui.PillButton(t("Remove", "Remover"), false);
        if (Audio.canCreateCable()) {
            first.add(Ui.body(t("A virtual cable and a microphone fed by it.", "Um cabo virtual e um microfone ligado a ele."), Theme.TEXT_DIM, 12, Font.PLAIN));
            first.add(Box.createVerticalStrut(8));
            first.add(cableState);
            first.add(Box.createVerticalStrut(10));
            first.add(Ui.row(create, remove));
        } else {
            first.add(Ui.body(Tts.MAC
                ? t("macOS needs a driver for a virtual microphone: install BlackHole 2ch once (free; or: brew install blackhole-2ch).",
                    "O macOS precisa de um driver para ter um microfone virtual: instale o BlackHole 2ch uma vez (grátis; ou: brew install blackhole-2ch).")
                : t("Windows needs a driver for a virtual microphone: install VB-Cable once.",
                    "O Windows precisa de um driver para ter um microfone virtual: instale o VB-Cable uma vez."), Theme.TEXT_DIM, 12, Font.PLAIN));
            first.add(Box.createVerticalStrut(8));
            first.add(Ui.link(Audio.driverLink(), Audio.driverUrl()));
        }
        page.add(first);
        page.add(Box.createVerticalStrut(12));

        // 2. where the voice is played
        Ui.Card second = step(2, t("Send the voice into it", "Mande a voz para ele"));
        second.add(Ui.body(Audio.canCreateCable() ? t("Pick \"SMPutilities TTS Cable\".", "Escolha \"SMPutilities TTS Cable\".")
            : t("Pick \"" + Audio.driverOutput() + "\".", "Escolha \"" + Audio.driverOutput() + "\"."), Theme.TEXT_DIM, 12, Font.PLAIN));
        second.add(Box.createVerticalStrut(8));
        JComboBox<Audio.Device> devices = combo();
        second.add(devices);
        second.add(Box.createVerticalStrut(6));
        JLabel saved = Ui.body(" ", Theme.OK, 11, Font.PLAIN);
        second.add(saved);
        page.add(second);
        page.add(Box.createVerticalStrut(12));

        // 3. hear it yourself
        Ui.Card third = step(3, t("Hear it yourself (optional)", "Ouça também (opcional)"));
        third.add(Ui.body(t("Also plays it on your headphones while the game hears it.", "Também toca nos seus fones enquanto o jogo ouve."),
            Theme.TEXT_DIM, 12, Font.PLAIN));
        third.add(Box.createVerticalStrut(8));
        JComboBox<Audio.Device> monitors = combo();
        third.add(monitors);
        third.add(Box.createVerticalStrut(6));
        JLabel monitorState = Ui.body(" ", Theme.OK, 11, Font.PLAIN);
        third.add(monitorState);
        page.add(third);
        page.add(Box.createVerticalStrut(12));

        // 4. what to pick in the voice chat
        Ui.Card fourth = step(4, t("Choose it in the voice chat", "Escolha no voice chat"));
        fourth.add(Ui.body(t("In Simple Voice Chat (or Discord), set the microphone to:", "No Simple Voice Chat (ou Discord), escolha o microfone:"),
            Theme.TEXT_DIM, 12, Font.PLAIN));
        fourth.add(Box.createVerticalStrut(6));
        JLabel micName = Ui.body(Audio.canCreateCable() ? Audio.MIC_DESCRIPTION : Audio.driverMicrophone(), Theme.ACCENT, 13, Font.BOLD);
        micName.setFont(Theme.mono(Font.BOLD, 13));
        fourth.add(micName);
        if (Tts.MAC) {
            fourth.add(Box.createVerticalStrut(6));
            fourth.add(Ui.body(t("macOS also asks for permission: allow Minecraft (or its launcher) in System Settings > Privacy & Security > Microphone.",
                "O macOS também pede permissão: libere o Minecraft (ou o launcher) em Ajustes do Sistema > Privacidade e Segurança > Microfone."),
                Theme.TEXT_DIM, 12, Font.PLAIN));
        }
        fourth.add(Box.createVerticalStrut(10));
        Ui.PillButton copyName = new Ui.PillButton(t("Copy name", "Copiar nome"), false);
        copyName.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(micName.getText()), null));
        Ui.PillButton refresh = new Ui.PillButton(t("Refresh", "Atualizar"), false);
        fourth.add(Ui.row(copyName, refresh));
        page.add(fourth);

        boolean[] populating = {false};
        Runnable reload = () -> {
            boolean exists = Audio.cableExists() && (!Audio.canCreateCable() || Audio.micExists());
            cableState.setText(exists ? t("Microphone ready", "Microfone pronto") : t("Not created yet", "Ainda não criado"));
            cableState.setForeground(exists ? Theme.OK : Theme.TEXT_DIM);
            create.setEnabled(!exists);
            remove.setEnabled(exists);

            // Filling the boxes fires their listeners; without this guard the
            // first item would overwrite the saved choice with "system default".
            populating[0] = true;
            try {
                devices.removeAllItems();
                devices.addItem(new Audio.Device("", t("System default device", "Dispositivo padrão do sistema")));
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
                    saved.setText(t("Saved: ", "Salvo: ") + match);
                }
                monitors.removeAllItems();
                monitors.addItem(new Audio.Device("", t("Off (only the game hears it)", "Desligado (só o jogo ouve)")));
                String activeMonitor = Audio.canCreateCable() ? Audio.loopbackDestination() : Config.monitorSink();
                if (activeMonitor == null) {
                    activeMonitor = "";
                }
                Audio.Device monitorMatch = null;
                for (Audio.Device device : Audio.sinks()) {
                    if (Audio.isCable(device.name())) {
                        continue;  // looping the cable into itself would feed back
                    }
                    monitors.addItem(device);
                    if (device.name().equals(activeMonitor)) {
                        monitorMatch = device;
                    }
                }
                if (monitorMatch != null) {
                    monitors.setSelectedItem(monitorMatch);
                    monitorState.setText(t("Playing on: ", "Tocando em: ") + monitorMatch);
                    monitorState.setForeground(Theme.OK);
                } else {
                    monitorState.setText(t("Not playing on your speakers", "Não toca nos seus alto-falantes"));
                    monitorState.setForeground(Theme.TEXT_DIM);
                }
            } finally {
                populating[0] = false;
            }
            refreshChecks();
        };

        devices.addActionListener(e -> {
            Audio.Device selected = (Audio.Device) devices.getSelectedItem();
            if (populating[0] || selected == null) {
                return;
            }
            Config.setOutputSink(selected.name());
            saved.setText(t("Saved: ", "Salvo: ") + selected);
            append(t("Voice output set to ", "Saída da voz: ") + selected);
            refreshChecks();
        });

        monitors.addActionListener(e -> {
            Audio.Device selected = (Audio.Device) monitors.getSelectedItem();
            if (populating[0] || selected == null) {
                return;
            }
            Config.setMonitorSink(selected.name());
            boolean off = selected.name().isBlank();
            String error = null;
            if (Audio.canCreateCable()) {
                if (off) {
                    Audio.removeLoopback();
                } else {
                    error = Audio.createLoopback(selected.name());
                }
            }
            if (error != null) {
                monitorState.setText(t("Failed: ", "Falhou: ") + error);
                monitorState.setForeground(Theme.ERR);
                append(t("Could not start monitoring: ", "Não foi possível ouvir também: ") + error);
                return;
            }
            monitorState.setText(off ? t("Not playing on your speakers", "Não toca nos seus alto-falantes") : t("Playing on: ", "Tocando em: ") + selected);
            monitorState.setForeground(off ? Theme.TEXT_DIM : Theme.OK);
            append(off ? t("Monitoring off", "Escuta desligada") : t("Also playing the voice on ", "Também tocando a voz em ") + selected);
        });

        create.addActionListener(e -> {
            String error = Audio.createCable();
            append(error == null ? t("Virtual microphone created", "Microfone virtual criado")
                : t("Could not create the cable: ", "Não foi possível criar o cabo: ") + error);
            reload.run();
        });
        remove.addActionListener(e -> {
            Audio.removeLoopback();
            Audio.removeCable();
            append(t("Virtual microphone removed", "Microfone virtual removido"));
            reload.run();
        });
        refresh.addActionListener(e -> reload.run());
        reload.run();
        return page;
    }

    /** A numbered step card. */
    private static Ui.Card step(int number, String title) {
        Ui.Card card = Ui.section(null);
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel badge = new JLabel(String.valueOf(number), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(Theme.ui(Font.BOLD, 12));
        badge.setForeground(Theme.ON_ACCENT);
        Dimension size = new Dimension(22, 22);
        badge.setPreferredSize(size);
        badge.setMaximumSize(size);
        head.add(badge);
        head.add(Box.createHorizontalStrut(10));
        head.add(Ui.body(title, Theme.TEXT, 14, Font.BOLD));
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        card.add(head);
        card.add(Box.createVerticalStrut(10));
        return card;
    }

    private static JComboBox<Audio.Device> combo() {
        JComboBox<Audio.Device> box = new JComboBox<>();
        box.setFont(Theme.ui(Font.PLAIN, 12));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        Ui.styleCombo(box);
        return box;
    }

    // ------------------------------------------------------------- activity

    private JComponent activityPage() {
        JPanel page = column();
        title(page, t("Activity", "Atividade"), t("Everything the voice app did, newest at the bottom.", "Tudo o que o app de voz fez, o mais novo embaixo."));
        logList.setFont(Theme.mono(Font.PLAIN, 11));
        logList.setForeground(Theme.TEXT_DIM);
        logList.setBackground(Theme.SURFACE);
        logList.setSelectionBackground(Theme.SURFACE_ALT);
        logList.setSelectionForeground(Theme.TEXT);
        logList.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(logList);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        Ui.styleScrollBar(scroll.getVerticalScrollBar());
        page.add(scroll);
        page.add(Box.createVerticalStrut(12));
        Ui.PillButton copy = new Ui.PillButton(t("Copy address", "Copiar endereço"), false);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("http://127.0.0.1:" + port), null));
        Ui.PillButton clear = new Ui.PillButton(t("Clear", "Limpar"), false);
        clear.addActionListener(e -> logModel.clear());
        page.add(Ui.row(copy, clear));
        return page;
    }

    // ------------------------------------------------------------- updates

    void setRunning(boolean value) {
        SwingUtilities.invokeLater(() -> {
            running = value;
            dot.setColor(value ? Theme.OK : Theme.ERR);
            stateLabel.setText(value ? t("Running", "Funcionando") : t("Stopped", "Parado"));
            refreshChecks();
        });
    }

    void setServed(int count) {
        SwingUtilities.invokeLater(() -> {
            servedValue.setText(String.valueOf(count));
            meter.pulse();
        });
    }

    void append(String line) {
        SwingUtilities.invokeLater(() -> {
            logModel.addElement("[" + LocalTime.now().format(CLOCK) + "]  " + line);
            while (logModel.size() > LOG_LIMIT) {
                logModel.remove(0);
            }
            logList.ensureIndexIsVisible(logModel.size() - 1);
            if (line.startsWith("Spoke: ")) {
                lastLine.setText("“" + line.substring(7) + "”");
                lastLine.setForeground(Theme.TEXT);
                meter.pulse();
            }
        });
    }
}
