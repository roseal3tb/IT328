import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.File;
import java.net.URL;

public class ClientGUI extends JFrame {
    private final Color COLOR_BG = new Color(255, 245, 247);
    private final Color COLOR_PRIMARY = new Color(255, 182, 193);
    private final Color COLOR_ACCENT = new Color(212, 175, 55);
    private final Color COLOR_DARK = new Color(80, 50, 70);

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private java.net.Socket socket;
    private java.io.PrintWriter out;
    private String myName;

    private DefaultListModel<String> waitingListModel = new DefaultListModel<>();
    private JList<String> waitingList = new JList<>(waitingListModel);
    private JButton btnPlayNow = new JButton("Ready to Play");
    private JLabel lblWaitingMessage = new JLabel(" ", JLabel.CENTER);

    private JLabel lblBrandImage = new JLabel();
    private JLabel lblQuestionText = new JLabel("WHAT BRAND IS THIS?", JLabel.CENTER);
    private JLabel lblTimer = new JLabel("60s", JLabel.CENTER);
    private JLabel lblRound = new JLabel("ROUND 1", JLabel.CENTER);
    private JTextArea areaScores = new JTextArea();
    private JTextField txtAnswer = new JTextField();
    private JLabel lblGameMessage = new JLabel("", JLabel.CENTER);
    
    private int currentLevel = 1;
    
    private Font loraFont;
    private Font loraBoldFont;
    private Font loraItalicFont;
    
    private Timer waitingRoomTimer;
    private int waitingCountdown = 30;

    public ClientGUI() {
        setTitle("Glam & Ping - Beauty Studio");
        setSize(1000, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setBackground(COLOR_BG);
        
        loadLoraFont();
        initLoginPanel();
        initWaitingPanel();
        initGamePanel();

        add(mainPanel);
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void loadLoraFont() {
        try {
            loraFont = new Font("Serif", Font.PLAIN, 16);
            loraBoldFont = new Font("Serif", Font.BOLD, 16);
            loraItalicFont = new Font("Serif", Font.ITALIC, 16);
            Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            for (Font f : fonts) {
                if (f.getName().toLowerCase().contains("lora")) {
                    loraFont = f.deriveFont(16f);
                    loraBoldFont = f.deriveFont(Font.BOLD, 16f);
                    loraItalicFont = f.deriveFont(Font.ITALIC, 16f);
                    break;
                }
            }
        } catch (Exception e) {
            loraFont = new Font("Serif", Font.PLAIN, 16);
            loraBoldFont = new Font("Serif", Font.BOLD, 16);
            loraItalicFont = new Font("Serif", Font.ITALIC, 16);
        }
    }

    private void initLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(COLOR_BG);

        JLabel logo = new JLabel("GLAM & PING", JLabel.CENTER);
        logo.setFont(loraBoldFont.deriveFont(Font.BOLD | Font.ITALIC, 52f));
        logo.setForeground(COLOR_PRIMARY);

        JLabel tagline = new JLabel("Beauty Knowledge Challenge", JLabel.CENTER);
        tagline.setFont(loraItalicFont.deriveFont(Font.ITALIC, 16f));
        tagline.setForeground(COLOR_DARK);

        JTextField nameIn = new JTextField(20);
        nameIn.setFont(loraFont.deriveFont(18f));
        nameIn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_PRIMARY, 2),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JButton btnIn = new JButton("Join Studio");
        styleButton(btnIn, COLOR_PRIMARY);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridx = 0;
        gbc.gridy = 0;
        p.add(logo, gbc);
        gbc.gridy = 1;
        p.add(tagline, gbc);
        gbc.gridy = 2;
        JLabel nameLabel = new JLabel("Your Name:");
        nameLabel.setFont(loraFont.deriveFont(14f));
        nameLabel.setForeground(COLOR_DARK);
        p.add(nameLabel, gbc);
        gbc.gridy = 3;
        p.add(nameIn, gbc);
        gbc.gridy = 4;
        p.add(btnIn, gbc);

        btnIn.addActionListener(e -> {
            String name = nameIn.getText().trim();
            if (!name.isEmpty()) {
                myName = name;
                connect(name);
            } else {
                JOptionPane.showMessageDialog(this, "Please enter your name.");
            }
        });

        mainPanel.add(p, "LOGIN");
    }

    private void initWaitingPanel() {
        JPanel p = new JPanel(new BorderLayout(20, 20));
        p.setBackground(COLOR_BG);
        p.setBorder(new EmptyBorder(40, 60, 40, 60));

        JLabel title = new JLabel("Waiting Room", JLabel.CENTER);
        title.setFont(loraBoldFont.deriveFont(Font.BOLD | Font.ITALIC, 40f));
        title.setForeground(COLOR_PRIMARY);

        waitingList.setFont(loraFont.deriveFont(20f));
        waitingList.setBackground(Color.WHITE);
        waitingList.setBorder(BorderFactory.createLineBorder(COLOR_PRIMARY, 2));
        
        JScrollPane scroll = new JScrollPane(waitingList);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_ACCENT, 2),
            "Connected Players (Max 4)",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            loraBoldFont.deriveFont(Font.BOLD, 18f),
            COLOR_ACCENT
        ));

        styleButton(btnPlayNow, COLOR_ACCENT);
        btnPlayNow.setFont(loraBoldFont.deriveFont(Font.BOLD, 22f));
        btnPlayNow.setEnabled(false);
        
        btnPlayNow.addActionListener(e -> {
            out.println("READY");
            btnPlayNow.setEnabled(false);
            btnPlayNow.setText("Waiting for others...");
            lblWaitingMessage.setText("You are ready. Waiting for other players to click Ready...");
        });

        JButton btnLeave = new JButton("Leave Studio");
        styleButton(btnLeave, new Color(180, 100, 100));
        btnLeave.setFont(loraFont.deriveFont(Font.BOLD, 16f));
        btnLeave.addActionListener(e -> {
            if (out != null) {
                out.println("LEAVE");
            }
            System.exit(0);
        });

        lblWaitingMessage.setFont(loraItalicFont.deriveFont(Font.ITALIC, 16f));
        lblWaitingMessage.setForeground(COLOR_ACCENT);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(scroll, BorderLayout.CENTER);
        
        JPanel southPanel = new JPanel(new BorderLayout(10, 10));
        southPanel.setOpaque(false);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnPlayNow);
        buttonPanel.add(btnLeave);
        
        southPanel.add(buttonPanel, BorderLayout.CENTER);
        southPanel.add(lblWaitingMessage, BorderLayout.SOUTH);
        
        p.add(title, BorderLayout.NORTH);
        p.add(centerPanel, BorderLayout.CENTER);
        p.add(southPanel, BorderLayout.SOUTH);

        mainPanel.add(p, "WAITING");
    }

    private void initGamePanel() {
        JPanel p = new JPanel(new BorderLayout(20, 20));
        p.setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(COLOR_PRIMARY);
        header.setBorder(new EmptyBorder(20, 30, 20, 30));

        lblRound.setFont(loraBoldFont.deriveFont(Font.BOLD, 28f));
        lblRound.setForeground(Color.WHITE);

        lblTimer.setFont(loraBoldFont.deriveFont(Font.BOLD, 28f));
        lblTimer.setForeground(Color.WHITE);

        header.add(lblRound, BorderLayout.WEST);
        header.add(lblTimer, BorderLayout.EAST);

        JPanel centerImagePanel = new JPanel(new BorderLayout());
        centerImagePanel.setBackground(Color.WHITE);
        centerImagePanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));
        
        lblBrandImage.setHorizontalAlignment(JLabel.CENTER);
        lblBrandImage.setVerticalAlignment(JLabel.CENTER);
        lblBrandImage.setPreferredSize(new Dimension(400, 300));
        lblBrandImage.setBorder(BorderFactory.createLineBorder(COLOR_ACCENT, 3));
        
        lblQuestionText.setFont(loraBoldFont.deriveFont(Font.BOLD, 32f));
        lblQuestionText.setForeground(COLOR_PRIMARY);
        lblQuestionText.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        
        centerImagePanel.add(lblBrandImage, BorderLayout.CENTER);
        centerImagePanel.add(lblQuestionText, BorderLayout.SOUTH);

        lblGameMessage.setFont(loraItalicFont.deriveFont(Font.ITALIC, 16f));
        lblGameMessage.setForeground(COLOR_ACCENT);
        lblGameMessage.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        areaScores.setEditable(false);
        areaScores.setFont(loraFont.deriveFont(Font.BOLD, 16f));
        areaScores.setBackground(new Color(255, 245, 247));
        areaScores.setBorder(BorderFactory.createLineBorder(COLOR_ACCENT, 2));

        JScrollPane scroll = new JScrollPane(areaScores);
        scroll.setPreferredSize(new Dimension(280, 0));
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_ACCENT, 2),
            "Leaderboard",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            loraBoldFont.deriveFont(Font.BOLD, 18f),
            COLOR_ACCENT
        ));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        footer.setBackground(new Color(255, 245, 247));

        txtAnswer.setFont(loraFont.deriveFont(20f));
        txtAnswer.setPreferredSize(new Dimension(400, 45));
        txtAnswer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_PRIMARY, 2),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        JButton btnSend = new JButton("Submit Answer");
        styleButton(btnSend, COLOR_PRIMARY);
        btnSend.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));

        btnSend.addActionListener(e -> sendAnswer());
        txtAnswer.addActionListener(e -> sendAnswer());

        footer.add(txtAnswer);
        footer.add(btnSend);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(centerImagePanel, BorderLayout.CENTER);
        centerPanel.add(lblGameMessage, BorderLayout.SOUTH);

        p.add(header, BorderLayout.NORTH);
        p.add(centerPanel, BorderLayout.CENTER);
        p.add(scroll, BorderLayout.EAST);
        p.add(footer, BorderLayout.SOUTH);

        mainPanel.add(p, "GAME");
    }

    private void styleButton(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(loraBoldFont.deriveFont(Font.BOLD, 18f));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void connect(String name) {
        try {
            socket = new java.net.Socket("localhost", 4000);
            out = new java.io.PrintWriter(socket.getOutputStream(), true);
            out.println(name);
            cardLayout.show(mainPanel, "WAITING");
            new Thread(this::listen).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server. Studio may be full or offline.");
        }
    }
    
    private void startWaitingCountdown() {
        if (waitingRoomTimer != null && waitingRoomTimer.isRunning()) {
            waitingRoomTimer.stop();
        }
        waitingCountdown = 30;
        waitingRoomTimer = new Timer(1000, e -> {
            if (waitingCountdown > 0) {
                waitingCountdown--;
                lblWaitingMessage.setText("Game starting in " + waitingCountdown + " seconds...");
            } else {
                waitingRoomTimer.stop();
                out.println("FORCE_START");
            }
        });
        waitingRoomTimer.start();
    }
    
    private String getImageFileNameForLevel(int level) {
        switch(level) {
            case 1: return "mac.png";
            case 2: return "sephora.png";
            case 3: return "huda_beauty.png";
            case 4: return "fenty_beauty.png";
            case 5: return "dior.png";
            case 6: return "rare_beauty.png";
            default: return null;
        }
    }
    
    private void loadBrandImage(int level) {
        String fileName = getImageFileNameForLevel(level);
        String desktopPath = System.getProperty("user.home") + "/Desktop/images/" + fileName;
        
        File imageFile = new File(desktopPath);
        if (imageFile.exists()) {
            ImageIcon originalIcon = new ImageIcon(imageFile.getAbsolutePath());
            Image scaledImage = originalIcon.getImage().getScaledInstance(350, 250, Image.SCALE_SMOOTH);
            lblBrandImage.setIcon(new ImageIcon(scaledImage));
            lblBrandImage.setText("");
        } else {
            URL imgURL = getClass().getResource("/images/" + fileName);
            if (imgURL != null) {
                ImageIcon originalIcon = new ImageIcon(imgURL);
                Image scaledImage = originalIcon.getImage().getScaledInstance(350, 250, Image.SCALE_SMOOTH);
                lblBrandImage.setIcon(new ImageIcon(scaledImage));
                lblBrandImage.setText("");
            } else {
                lblBrandImage.setIcon(null);
                lblBrandImage.setText("[" + getBrandNameForLevel(level) + "]");
                lblBrandImage.setFont(loraFont.deriveFont(24f));
                lblBrandImage.setForeground(COLOR_DARK);
            }
        }
    }
    
    private String getBrandNameForLevel(int level) {
        switch(level) {
            case 1: return "MAC";
            case 2: return "SEPHORA";
            case 3: return "HUDA BEAUTY";
            case 4: return "FENTY BEAUTY";
            case 5: return "DIOR";
            case 6: return "RARE BEAUTY";
            default: return "???";
        }
    }

    private void listen() {
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
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
        if (m.startsWith("USERS:")) {
            String raw = m.substring(6);
            waitingListModel.clear();
            if (!raw.isEmpty()) {
                String[] users = raw.split(",");
                for (String user : users) {
                    if (!user.isEmpty()) {
                        waitingListModel.addElement(user);
                    }
                }
            }
            updateWaitingRoomStatus();
        } 
        else if (m.startsWith("COUNTDOWN:")) {
            String count = m.substring(10);
            lblWaitingMessage.setText("Game starting in " + count + " seconds...");
            if (count.equals("0")) {
                lblWaitingMessage.setText("Starting game...");
            }
        } 
        else if (m.equals("GAME_STARTED") || m.equals("START_GAME")) {
            if (waitingRoomTimer != null && waitingRoomTimer.isRunning()) {
                waitingRoomTimer.stop();
            }
            cardLayout.show(mainPanel, "GAME");
            currentLevel = 1;
            loadBrandImage(currentLevel);
            lblGameMessage.setText("Game started! Guess the beauty brand!");
        } 
        else if (m.startsWith("TIME:")) {
            lblTimer.setText(m.substring(5) + "s");
            if (Integer.parseInt(m.substring(5)) <= 10) {
                lblTimer.setForeground(Color.RED);
            } else {
                lblTimer.setForeground(Color.WHITE);
            }
        } 
        else if (m.startsWith("SCORES:")) {
            String scores = m.substring(7);
            areaScores.setText(scores.replace(",", "\n").replace("-", " : "));
        } 
        else if (m.startsWith("NEXT_LEVEL:")) {
            currentLevel = Integer.parseInt(m.substring(11));
            lblRound.setText("ROUND " + currentLevel);
            loadBrandImage(currentLevel);
            lblGameMessage.setText("Type your answer below!");
            lblTimer.setForeground(Color.WHITE);
        } 
        else if (m.startsWith("WINNER:")) {
            String winner = m.substring(7);
            lblGameMessage.setText(winner.toUpperCase() + " got it right! +10 points!");
            Timer timer = new Timer(2500, e -> lblGameMessage.setText("Next round coming up..."));
            timer.setRepeats(false);
            timer.start();
        } 
        else if (m.equals("FINAL")) {
            JOptionPane.showMessageDialog(this, 
                "GAME OVER!\nCheck the leaderboard to see the winner!\nCongratulations to all players!",
                "Game Complete",
                JOptionPane.INFORMATION_MESSAGE);
            resetForNewGame();
            cardLayout.show(mainPanel, "WAITING");
            btnPlayNow.setEnabled(true);
            btnPlayNow.setText("Ready to Play");
            lblWaitingMessage.setText(" ");
        }
        else if (m.equals("TIME_UP_NO_WINNER")) {
            lblGameMessage.setText("Time's up! No one answered correctly.");
            Timer timer = new Timer(2000, e -> out.println("NEXT_ROUND"));
            timer.setRepeats(false);
            timer.start();
        }
        else if (m.startsWith("WAITING_MSG:")) {
            lblWaitingMessage.setText(m.substring(11));
        } 
        else if (m.equals("FULL")) {
            JOptionPane.showMessageDialog(this, 
                "Studio is full! Maximum 4 players allowed.\nPlease try again later.",
                "Studio Full",
                JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }
        else if (m.startsWith("ERROR:")) {
            JOptionPane.showMessageDialog(this, m.substring(6), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        else if (m.equals("RESET_READY_BUTTON")) {
            btnPlayNow.setEnabled(true);
            btnPlayNow.setText("Ready to Play");
        }
        else if (m.startsWith("PLAYER_JOINED:")) {
            String playerName = m.substring(14);
            lblWaitingMessage.setText(playerName + " joined the waiting room!");
        }
        else if (m.startsWith("PLAYER_LEFT:")) {
            String playerName = m.substring(12);
            lblWaitingMessage.setText(playerName + " left the studio.");
        }
    }

    private void updateWaitingRoomStatus() {
        int playerCount = waitingListModel.size();
        
        if (playerCount == 1) {
            lblWaitingMessage.setText("Waiting for at least 2 players to join... (You are alone)");
            btnPlayNow.setEnabled(false);
            btnPlayNow.setText("Need more players");
            if (waitingRoomTimer != null && waitingRoomTimer.isRunning()) {
                waitingRoomTimer.stop();
            }
        } 
        else if (playerCount >= 2 && playerCount <= 4) {
            btnPlayNow.setEnabled(true);
            btnPlayNow.setText("Ready to Play");
            lblWaitingMessage.setText(playerCount + " players connected. Click 'Ready to Play' when ready!");
            startWaitingCountdown();
        }
        
        if (playerCount >= 4) {
            lblWaitingMessage.setText("Studio is full! Everyone click Ready to start!");
        }
    }

    private void resetForNewGame() {
        lblRound.setText("ROUND 1");
        lblQuestionText.setText("WHAT BRAND IS THIS?");
        lblBrandImage.setIcon(null);
        areaScores.setText("");
        txtAnswer.setText("");
        lblTimer.setText("60s");
        lblTimer.setForeground(Color.WHITE);
        lblGameMessage.setText("");
        currentLevel = 1;
        if (waitingRoomTimer != null && waitingRoomTimer.isRunning()) {
            waitingRoomTimer.stop();
        }
    }

    private void sendAnswer() {
        String answer = txtAnswer.getText().trim();
        if (!answer.isEmpty()) {
            out.println("ANSWER:" + answer);
            txtAnswer.setText("");
            lblGameMessage.setText("Answer submitted! Waiting for result...");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI());
    }
}