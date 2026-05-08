import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.File;
import java.net.URL;

public class ClientGUI extends JFrame {
    // الألوان - تدرجات وردي ناعم
    private final Color COLOR_BG = new Color(255, 248, 245);
    private final Color COLOR_CONNECTION = new Color(255, 242, 248);
    private final Color COLOR_PLAYROOM = new Color(255, 235, 242);
    private final Color COLOR_PRIMARY = new Color(230, 120, 150);
    private final Color COLOR_SECONDARY = new Color(245, 180, 190);
    private final Color COLOR_ACCENT = new Color(210, 140, 160);
    private final Color COLOR_DARK = new Color(80, 60, 65);

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private java.net.Socket socket;
    private java.io.PrintWriter out;
    private String myName;

    // Connection Room (غير محدود)
    private DefaultListModel<String> connectionListModel = new DefaultListModel<>();
    private JList<String> connectionList = new JList<>(connectionListModel);
    private JLabel lblConnStatus = new JLabel(" ", JLabel.CENTER);
    private JButton btnJoinPlayRoom = new JButton("Join Play Room");

    // Play Room (حد أقصى 4)
    private DefaultListModel<String> playRoomListModel = new DefaultListModel<>();
    private JList<String> playRoomList = new JList<>(playRoomListModel);
    private JLabel lblPlayStatus = new JLabel(" ", JLabel.CENTER);
    private JButton btnReady = new JButton("Ready");

    // Game Room
    private JLabel lblBrandImage = new JLabel();
    private JLabel lblQuestionText = new JLabel("WHAT BRAND IS THIS?", JLabel.CENTER);
    private JLabel lblTimer = new JLabel("60s", JLabel.CENTER);
    private JLabel lblRound = new JLabel("Round 1", JLabel.CENTER);
    private JTextArea areaScores = new JTextArea();
    private JTextField txtAnswer = new JTextField();
    private JLabel lblGameMessage = new JLabel("", JLabel.CENTER);
    
    private int currentLevel = 1;
    private Font loraFont, loraBoldFont, loraItalicFont;
    private Timer playRoomTimer;

    public ClientGUI() {
        setTitle("Glam & Ping - Beauty Studio");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setBackground(COLOR_BG);
        setUndecorated(true);

        loadLoraFont();
        initCustomTitleBar();
        initLoginPanel();
        initConnectionPanel();
        initPlayRoomPanel();
        initGamePanel();

        add(mainPanel);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void loadLoraFont() {
        try {
            Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            boolean found = false;
            for (Font f : fonts) {
                if (f.getName().toLowerCase().contains("lora")) {
                    loraFont = f.deriveFont(16f);
                    loraBoldFont = f.deriveFont(Font.BOLD, 16f);
                    loraItalicFont = f.deriveFont(Font.ITALIC, 16f);
                    found = true;
                    break;
                }
            }
            if (!found) {
                loraFont = new Font("Serif", Font.PLAIN, 16);
                loraBoldFont = new Font("Serif", Font.BOLD, 16);
                loraItalicFont = new Font("Serif", Font.ITALIC, 16);
            }
        } catch (Exception e) {
            loraFont = new Font("Serif", Font.PLAIN, 16);
            loraBoldFont = new Font("Serif", Font.BOLD, 16);
            loraItalicFont = new Font("Serif", Font.ITALIC, 16);
        }
    }

    private void initCustomTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(COLOR_PRIMARY);
        titleBar.setPreferredSize(new Dimension(1200, 45));

        JLabel titleLabel = new JLabel("Glam & Ping");
        titleLabel.setFont(loraBoldFont.deriveFont(Font.BOLD, 20f));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("Serif", Font.BOLD, 18));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(COLOR_PRIMARY);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> System.exit(0));

        JButton minimizeBtn = new JButton("─");
        minimizeBtn.setFont(new Font("Serif", Font.BOLD, 18));
        minimizeBtn.setForeground(Color.WHITE);
        minimizeBtn.setBackground(COLOR_PRIMARY);
        minimizeBtn.setBorderPainted(false);
        minimizeBtn.setFocusPainted(false);
        minimizeBtn.addActionListener(e -> setState(Frame.ICONIFIED));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(COLOR_PRIMARY);
        buttonPanel.add(minimizeBtn);
        buttonPanel.add(closeBtn);

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(buttonPanel, BorderLayout.EAST);
        add(titleBar, BorderLayout.NORTH);
    }

    private void initLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(COLOR_BG);
        
        JPanel gradientPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(0, 0, new Color(255, 230, 235), 
                    getWidth(), getHeight(), new Color(255, 210, 220));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        gradientPanel.setLayout(new GridBagLayout());
        gradientPanel.setBorder(BorderFactory.createEmptyBorder(50, 100, 50, 100));

        JLabel logo = new JLabel("GLAM & PING", JLabel.CENTER);
        logo.setFont(loraBoldFont.deriveFont(Font.BOLD | Font.ITALIC, 58f));
        logo.setForeground(COLOR_PRIMARY);

        JLabel tagline = new JLabel("Beauty Knowledge Challenge", JLabel.CENTER);
        tagline.setFont(loraItalicFont.deriveFont(Font.ITALIC, 20f));
        tagline.setForeground(COLOR_DARK);

        JTextField nameIn = new JTextField(20);
        nameIn.setFont(loraFont.deriveFont(20f));
        nameIn.setForeground(COLOR_DARK);
        nameIn.setBackground(Color.WHITE);
        nameIn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_PRIMARY, 2),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        nameIn.setCaretColor(COLOR_PRIMARY);

        JButton btnIn = new JButton("Join Studio");
        styleButton(btnIn, COLOR_PRIMARY);
        btnIn.setFont(loraBoldFont.deriveFont(Font.BOLD, 22f));
        btnIn.setPreferredSize(new Dimension(300, 60));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gradientPanel.add(logo, gbc);
        gbc.gridy = 1;
        gradientPanel.add(tagline, gbc);
        gbc.gridy = 2;
        gbc.insets = new Insets(40, 20, 20, 20);
        gradientPanel.add(nameIn, gbc);
        gbc.gridy = 3;
        gradientPanel.add(btnIn, gbc);

        btnIn.addActionListener(e -> {
            String name = nameIn.getText().trim();
            if (!name.isEmpty()) {
                myName = name;
                connect(name);
            } else {
                JOptionPane.showMessageDialog(this, "Please enter your name.");
            }
        });

        p.add(gradientPanel);
        mainPanel.add(p, "LOGIN");
    }

    private void initConnectionPanel() {
        JPanel p = new JPanel(new BorderLayout(20, 20));
        p.setBackground(COLOR_CONNECTION);
        p.setBorder(new EmptyBorder(40, 60, 40, 60));

        JLabel title = new JLabel("Connection Room", JLabel.CENTER);
        title.setFont(loraBoldFont.deriveFont(Font.BOLD | Font.ITALIC, 44f));
        title.setForeground(COLOR_PRIMARY);

        connectionList.setFont(loraFont.deriveFont(18f));
        connectionList.setBackground(Color.WHITE);
        connectionList.setForeground(COLOR_DARK);
        connectionList.setBorder(BorderFactory.createLineBorder(COLOR_PRIMARY, 2));
        JScrollPane scroll = new JScrollPane(connectionList);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_ACCENT, 2),
            "All Connected Players",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            loraBoldFont.deriveFont(Font.BOLD, 16f),
            COLOR_ACCENT
        ));
        scroll.getViewport().setBackground(Color.WHITE);

        styleButton(btnJoinPlayRoom, COLOR_PRIMARY);
        btnJoinPlayRoom.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));
        btnJoinPlayRoom.setEnabled(false);
        btnJoinPlayRoom.addActionListener(e -> {
            out.println("JOIN_PLAYROOM");
            btnJoinPlayRoom.setEnabled(false);
            btnJoinPlayRoom.setText("Joining...");
        });

        JButton btnLeaveConn = new JButton("Leave Studio");
        styleButton(btnLeaveConn, new Color(200, 130, 140));
        btnLeaveConn.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));
        btnLeaveConn.addActionListener(e -> {
            if (out != null) out.println("LEAVE");
            System.exit(0);
        });

        // توحيد الارتفاع فقط
        setUniformHeight(btnJoinPlayRoom, btnLeaveConn);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottom.setOpaque(false);
        bottom.add(btnJoinPlayRoom);
        bottom.add(btnLeaveConn);

        lblConnStatus.setFont(loraItalicFont.deriveFont(Font.ITALIC, 16f));
        lblConnStatus.setForeground(COLOR_ACCENT);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setOpaque(false);
        southPanel.add(bottom, BorderLayout.CENTER);
        southPanel.add(lblConnStatus, BorderLayout.SOUTH);

        p.add(title, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(southPanel, BorderLayout.SOUTH);

        mainPanel.add(p, "CONNECTION");
    }

    private void initPlayRoomPanel() {
        JPanel p = new JPanel(new BorderLayout(20, 20));
        p.setBackground(COLOR_PLAYROOM);
        p.setBorder(new EmptyBorder(40, 60, 40, 60));

        JLabel title = new JLabel("Play Room", JLabel.CENTER);
        title.setFont(loraBoldFont.deriveFont(Font.BOLD | Font.ITALIC, 44f));
        title.setForeground(COLOR_PRIMARY);

        playRoomList.setFont(loraFont.deriveFont(20f));
        playRoomList.setBackground(Color.WHITE);
        playRoomList.setForeground(COLOR_DARK);
        playRoomList.setSelectionBackground(new Color(230, 120, 150, 80));
        playRoomList.setBorder(BorderFactory.createLineBorder(COLOR_PRIMARY, 2));
        JScrollPane scroll = new JScrollPane(playRoomList);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_ACCENT, 2),
            "Players in Play Room",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            loraBoldFont.deriveFont(Font.BOLD, 18f),
            COLOR_ACCENT
        ));
        scroll.getViewport().setBackground(Color.WHITE);

        styleButton(btnReady, COLOR_SECONDARY);
        btnReady.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));
        btnReady.setEnabled(false);
        btnReady.addActionListener(e -> {
            out.println("READY");
            btnReady.setEnabled(false);
            btnReady.setText("Waiting...");
            lblPlayStatus.setText("You are ready. Waiting for others...");
        });

        JButton btnLeavePlay = new JButton("Leave Play Room");
        styleButton(btnLeavePlay, new Color(200, 130, 140));
        btnLeavePlay.setFont(loraFont.deriveFont(Font.BOLD, 16f));
        btnLeavePlay.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Leave the play room? You will return to connection room.",
                "Leave", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                out.println("LEAVE_PLAYROOM");
                cardLayout.show(mainPanel, "CONNECTION");
                btnReady.setEnabled(false);
                btnReady.setText("Ready");
            }
        });

        // توحيد الارتفاع فقط
        setUniformHeight(btnReady, btnLeavePlay);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnReady);
        buttonPanel.add(btnLeavePlay);

        lblPlayStatus.setFont(loraItalicFont.deriveFont(Font.ITALIC, 18f));
        lblPlayStatus.setForeground(COLOR_PRIMARY);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setOpaque(false);
        southPanel.add(buttonPanel, BorderLayout.CENTER);
        southPanel.add(lblPlayStatus, BorderLayout.SOUTH);

        p.add(title, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(southPanel, BorderLayout.SOUTH);

        mainPanel.add(p, "PLAYROOM");
    }

    private void initGamePanel() {
        JPanel p = new JPanel(new BorderLayout(20, 20));
        p.setBackground(COLOR_BG);

        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(COLOR_PRIMARY);
        header.setBorder(new EmptyBorder(20, 30, 20, 30));

        lblRound.setFont(loraBoldFont.deriveFont(Font.BOLD, 28f));
        lblRound.setForeground(Color.WHITE);
        lblTimer.setFont(loraBoldFont.deriveFont(Font.BOLD, 32f));
        lblTimer.setForeground(Color.WHITE);

        header.add(lblRound, BorderLayout.WEST);
        header.add(lblTimer, BorderLayout.EAST);

        JPanel centerImagePanel = new JPanel(new BorderLayout());
        centerImagePanel.setBackground(COLOR_BG);
        centerImagePanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        lblBrandImage.setHorizontalAlignment(JLabel.CENTER);
        lblBrandImage.setVerticalAlignment(JLabel.CENTER);
        lblBrandImage.setPreferredSize(new Dimension(500, 400));
        lblBrandImage.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_PRIMARY, 3),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        lblQuestionText.setFont(loraBoldFont.deriveFont(Font.BOLD, 28f));
        lblQuestionText.setForeground(COLOR_PRIMARY);
        lblQuestionText.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        centerImagePanel.add(lblBrandImage, BorderLayout.CENTER);
        centerImagePanel.add(lblQuestionText, BorderLayout.SOUTH);

        lblGameMessage.setFont(loraItalicFont.deriveFont(Font.ITALIC, 18f));
        lblGameMessage.setForeground(COLOR_ACCENT);
        lblGameMessage.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        areaScores.setEditable(false);
        areaScores.setFont(loraFont.deriveFont(Font.BOLD, 18f));
        areaScores.setBackground(new Color(255, 250, 248));
        areaScores.setForeground(COLOR_DARK);
        areaScores.setBorder(BorderFactory.createLineBorder(COLOR_PRIMARY, 2));

        JScrollPane scroll = new JScrollPane(areaScores);
        scroll.setPreferredSize(new Dimension(300, 0));
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_ACCENT, 2),
            "Leaderboard",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            loraBoldFont.deriveFont(Font.BOLD, 18f),
            COLOR_ACCENT
        ));
        scroll.getViewport().setBackground(new Color(255, 250, 248));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        footer.setBackground(new Color(255, 245, 242));
        footer.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        txtAnswer.setFont(loraFont.deriveFont(20f));
        txtAnswer.setPreferredSize(new Dimension(400, 50));
        txtAnswer.setForeground(COLOR_DARK);
        txtAnswer.setBackground(Color.WHITE);
        txtAnswer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_PRIMARY, 2),
            BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        txtAnswer.setCaretColor(COLOR_PRIMARY);

        JButton btnSend = new JButton("Submit Answer");
        styleButton(btnSend, COLOR_PRIMARY);
        btnSend.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));

        JButton btnGameLeave = new JButton("Leave Game");
        styleButton(btnGameLeave, new Color(200, 130, 140));
        btnGameLeave.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));
        btnGameLeave.addActionListener(e -> leaveGame());

        // توحيد الارتفاع فقط
        setUniformHeight(btnSend, btnGameLeave);

        btnSend.addActionListener(e -> sendAnswer());
        txtAnswer.addActionListener(e -> sendAnswer());

        footer.add(txtAnswer);
        footer.add(btnSend);
        footer.add(btnGameLeave);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(centerImagePanel, BorderLayout.CENTER);
        centerPanel.add(lblGameMessage, BorderLayout.SOUTH);

        p.add(header, BorderLayout.NORTH);
        p.add(centerPanel, BorderLayout.CENTER);
        p.add(scroll, BorderLayout.EAST);
        p.add(footer, BorderLayout.SOUTH);

        mainPanel.add(p, "GAME");
    }

    private void setUniformHeight(JButton... buttons) {
        int maxHeight = 50;
        
        for (JButton btn : buttons) {
            FontMetrics fm = btn.getFontMetrics(btn.getFont());
            int textHeight = fm.getHeight();
            maxHeight = Math.max(maxHeight, textHeight + 24);
        }
        
        for (JButton btn : buttons) {
            Dimension d = btn.getPreferredSize();
            btn.setPreferredSize(new Dimension(d.width, maxHeight));
            btn.setMinimumSize(new Dimension(d.width, maxHeight));
        }
    }

    private void styleButton(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(color);
            }
        });
    }

    private void connect(String name) {
        try {
            socket = new java.net.Socket("localhost", 4000);
            out = new java.io.PrintWriter(socket.getOutputStream(), true);
            out.println(name);
            cardLayout.show(mainPanel, "CONNECTION");
            new Thread(this::listen).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server.");
        }
    }

    private void leaveGame() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to leave the game?\nYour progress will be lost.",
            "Leave Game", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            out.println("LEAVE_GAME");
            resetGame();
            cardLayout.show(mainPanel, "CONNECTION");
            lblConnStatus.setText("You left the game.");
        }
    }

    private void resetGame() {
        currentLevel = 1;
        lblRound.setText("Round 1");
        lblQuestionText.setText("WHAT BRAND IS THIS?");
        lblBrandImage.setIcon(null);
        areaScores.setText("");
        txtAnswer.setText("");
        lblTimer.setText("60s");
        lblGameMessage.setText("");
        if (playRoomTimer != null) playRoomTimer.stop();
    }

    private void sendAnswer() {
        String ans = txtAnswer.getText().trim();
        if (!ans.isEmpty()) {
            out.println("ANSWER:" + ans);
            txtAnswer.setText("");
            lblGameMessage.setText("Answer submitted...");
        }
    }

    private void loadBrandImage(int level) {
        String[] files = {"mac.png", "sephora.png", "huda_beauty.png", "fenty_beauty.png", "dior.png", "rare_beauty.png"};
        String[] names = {"MAC", "SEPHORA", "HUDA BEAUTY", "FENTY BEAUTY", "DIOR", "RARE BEAUTY"};
        if (level < 1 || level > files.length) return;
        String path = System.getProperty("user.home") + "/Documents/GitHub/IT328/images/" + files[level-1];
        File f = new File(path);
        if (f.exists()) {
            ImageIcon icon = new ImageIcon(path);
            Image img = icon.getImage().getScaledInstance(450, 350, Image.SCALE_SMOOTH);
            lblBrandImage.setIcon(new ImageIcon(img));
            lblBrandImage.setText("");
        } else {
            lblBrandImage.setIcon(null);
            lblBrandImage.setText("[" + names[level-1] + "]");
            lblBrandImage.setFont(loraFont.deriveFont(28f));
            lblBrandImage.setForeground(COLOR_PRIMARY);
        }
    }

    private void listen() {
        try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()))) {
            String msg;
            while ((msg = in.readLine()) != null) {
                final String m = msg;
                SwingUtilities.invokeLater(() -> handle(m));
            }
        } catch (Exception e) {
            System.out.println("Connection lost");
        }
    }

    private void handle(String m) {
        if (m.startsWith("CONNECTION_USERS:")) {
            String raw = m.substring(17);
            connectionListModel.clear();
            for (String u : raw.split(",")) if (!u.isEmpty()) connectionListModel.addElement(u);
            int myIdx = connectionListModel.indexOf(myName);
            if (myIdx >= 0 && myIdx < 4 && !playRoomListModel.contains(myName)) {
                btnJoinPlayRoom.setEnabled(true);
                btnJoinPlayRoom.setText("Join Play Room");
                lblConnStatus.setText("You can join the Play Room (first 4 players)");
            } else if (playRoomListModel.contains(myName)) {
                btnJoinPlayRoom.setEnabled(false);
                btnJoinPlayRoom.setText("In Play Room");
                lblConnStatus.setText("You are in Play Room");
            } else {
                btnJoinPlayRoom.setEnabled(false);
                btnJoinPlayRoom.setText("Join Play Room");
                lblConnStatus.setText("Play room is full or game in progress. You are in spectator mode.");
            }
        }
        else if (m.startsWith("PLAYROOM_USERS:")) {
            String raw = m.substring(14);
            playRoomListModel.clear();
            for (String u : raw.split(",")) if (!u.isEmpty()) playRoomListModel.addElement(u);
            if (playRoomListModel.contains(myName)) {
                if (!mainPanel.getComponent(0).getName().equals("PLAYROOM")) {
                    cardLayout.show(mainPanel, "PLAYROOM");
                }
                btnReady.setEnabled(true);
                btnReady.setText("Ready");
                lblPlayStatus.setText("Click Ready when you are ready");
            }
        }
        else if (m.equals("PLAYROOM_JOINED")) {
            cardLayout.show(mainPanel, "PLAYROOM");
            btnReady.setEnabled(true);
            lblPlayStatus.setText("Click Ready when you are ready");
        }
        else if (m.equals("PLAYROOM_FULL")) {
            lblConnStatus.setText("Play room is full (4 players). You are in spectator mode.");
            btnJoinPlayRoom.setEnabled(false);
        }
        else if (m.equals("GAME_ALREADY_STARTED")) {
            lblConnStatus.setText("Game already in progress. Please wait for next round.");
            btnJoinPlayRoom.setEnabled(false);
        }
        else if (m.startsWith("COUNTDOWN:")) {
            String sec = m.substring(10);
            lblPlayStatus.setText("Game starting in " + sec + " seconds");
            if (sec.equals("0")) lblPlayStatus.setText("Starting game...");
        }
        else if (m.equals("GAME_STARTED")) {
            cardLayout.show(mainPanel, "GAME");
            currentLevel = 1;
            loadBrandImage(1);
            lblGameMessage.setText("Game started! Guess the brand!");
        }
        else if (m.startsWith("TIME:")) {
            lblTimer.setText(m.substring(5) + "s");
            int t = Integer.parseInt(m.substring(5));
            lblTimer.setForeground(t <= 10 ? Color.RED : Color.WHITE);
        }
        else if (m.startsWith("SCORES:")) {
            String scores = m.substring(7);
            areaScores.setText(scores.replace(",", "\n").replace("-", " : "));
        }
        else if (m.startsWith("NEXT_LEVEL:")) {
            currentLevel = Integer.parseInt(m.substring(11));
            lblRound.setText("Round " + currentLevel);
            loadBrandImage(currentLevel);
            lblGameMessage.setText("New round! Type your answer.");
        }
        else if (m.startsWith("WINNER:")) {
            String winner = m.substring(7);
            lblGameMessage.setText(winner.toUpperCase() + " got it right! +10 points");
            Timer t = new Timer(2500, e -> lblGameMessage.setText("Next round..."));
            t.setRepeats(false);
            t.start();
        }
        else if (m.equals("FINAL")) {
            JOptionPane.showMessageDialog(this, "Game Over! Check leaderboard for winner.");
            resetGame();
            cardLayout.show(mainPanel, "CONNECTION");
            out.println("RETURN_TO_CONNECTION");
        }
        else if (m.equals("TIME_UP_NO_WINNER")) {
            lblGameMessage.setText("Time's up! No one answered.");
            Timer t = new Timer(2000, e -> out.println("NEXT_ROUND"));
            t.setRepeats(false);
            t.start();
        }
        else if (m.startsWith("WAITING_MSG:")) {
            String msg = m.substring(11);
            if (mainPanel.getComponent(0) != null && 
                ((JPanel)mainPanel.getComponent(0)).getBackground().equals(COLOR_PLAYROOM)) {
                lblPlayStatus.setText(msg);
            } else {
                lblConnStatus.setText(msg);
            }
        }
        else if (m.startsWith("PLAYER_JOINED:")) {
            String pname = m.substring(14);
            lblConnStatus.setText(pname + " joined the studio");
        }
        else if (m.startsWith("PLAYER_LEFT:")) {
            String pname = m.substring(12);
            lblConnStatus.setText(pname + " left");
        }
        else if (m.equals("GAME_LEFT")) {
            resetGame();
            cardLayout.show(mainPanel, "CONNECTION");
            lblConnStatus.setText("You left the game.");
        }
        else if (m.startsWith("ERROR:")) {
            JOptionPane.showMessageDialog(this, m.substring(6), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI());
    }
}