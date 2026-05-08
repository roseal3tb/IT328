import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Timer;

public class NewServer {
    private static final Map<String, NewClientHandler> allClients = new LinkedHashMap<>();
    private static final Set<String> playRoomPlayers = new LinkedHashSet<>();
    private static final Set<String> readyPlayers = new HashSet<>();
    private static final HashMap<String, Integer> playerScores = new HashMap<>();
    private static final int MAX_PLAYROOM = 4;

    private static boolean gameInProgress = false;
    private static int currentLevel = 1;
    private static Timer gameTimer;
    private static Timer playRoomTimer;
    private static boolean countdownRunning = false;
    private static int countdownSeconds = 10;

    private static final Map<Integer, String> ANSWERS = Map.of(
        1, "mac", 2, "sephora", 3, "huda beauty",
        4, "fenty beauty", 5, "dior", 6, "rare beauty"
    );

    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(4000);
        System.out.println("Glam & Ping Server Started");
        System.out.println("Connection Room: Unlimited players");
        System.out.println("Play Room: Max " + MAX_PLAYROOM + " players");
        System.out.println("Players must click 'Join Play Room' to enter.");
        while (true) {
            Socket s = ss.accept();
            new Thread(new NewClientHandler(s)).start();
        }
    }

    public static synchronized void addClient(String name, NewClientHandler handler) {
        if (allClients.containsKey(name)) {
            handler.send("ERROR:Name already taken");
            return;
        }
        allClients.put(name, handler);
        playerScores.put(name, 0);
        broadcastConnectionList();
        broadcast("PLAYER_JOINED:" + name);
        System.out.println("+ " + name + " joined Connection Room (Total: " + allClients.size() + ")");
    }

    public static synchronized void joinPlayRoom(String name) {
        if (gameInProgress) {
            NewClientHandler h = allClients.get(name);
            if (h != null) h.send("GAME_ALREADY_STARTED");
            return;
        }
        if (playRoomPlayers.size() >= MAX_PLAYROOM) {
            NewClientHandler h = allClients.get(name);
            if (h != null) h.send("PLAYROOM_FULL");
            return;
        }
        if (!playRoomPlayers.contains(name)) {
            playRoomPlayers.add(name);
            broadcastPlayRoomList();
            broadcastConnectionList();
            NewClientHandler h = allClients.get(name);
            if (h != null) h.send("PLAYROOM_JOINED");
            System.out.println(name + " joined Play Room (" + playRoomPlayers.size() + "/" + MAX_PLAYROOM + ")");
            
            // طباعة حالة اللاعبين في Play Room
            System.out.println("Current Play Room players: " + playRoomPlayers);
        }
    }

    public static synchronized void leavePlayRoom(String name) {
        if (gameInProgress) return;
        playRoomPlayers.remove(name);
        readyPlayers.remove(name);
        broadcastPlayRoomList();
        broadcastConnectionList();
        System.out.println(name + " left Play Room. Remaining: " + playRoomPlayers.size());
        
        // إذا كان هناك مؤقت جاري وأصبح العدد أقل من 2، ألغِ العد التنازلي
        if (playRoomPlayers.size() < 2 && playRoomTimer != null) {
            playRoomTimer.cancel();
            playRoomTimer = null;
            countdownRunning = false;
            broadcast("WAITING_MSG:Need at least 2 players in Play Room to start.");
        }
        // إذا كان العدد 2 أو أكثر وكان الجميع جاهزين، ابدأ العد
        if (playRoomPlayers.size() >= 2 && readyPlayers.size() == playRoomPlayers.size() && !countdownRunning && playRoomTimer == null) {
            startPlayRoomCountdown();
        }
    }

    public static synchronized void playerReady(String name) {
        if (!playRoomPlayers.contains(name)) {
            System.out.println(name + " tried to ready but not in Play Room!");
            return;
        }
        if (gameInProgress) {
            System.out.println(name + " tried to ready but game in progress!");
            return;
        }
        if (!readyPlayers.contains(name)) {
            readyPlayers.add(name);
            broadcastPlayRoomList();
            broadcast("WAITING_MSG:" + name + " is ready (" + readyPlayers.size() + "/" + playRoomPlayers.size() + ")");
            System.out.println(name + " is ready (" + readyPlayers.size() + "/" + playRoomPlayers.size() + ")");
            
            // التحقق: إذا كان عدد الجاهزين يساوي عدد اللاعبين في Play Room
            // وعدد اللاعبين 2 على الأقل
            if (readyPlayers.size() == playRoomPlayers.size() && playRoomPlayers.size() >= 2 && !countdownRunning && playRoomTimer == null) {
                startPlayRoomCountdown();
            } else if (playRoomPlayers.size() < 2) {
                broadcast("WAITING_MSG:Need at least 2 players in Play Room. Current: " + playRoomPlayers.size());
            } else if (readyPlayers.size() < playRoomPlayers.size()) {
                broadcast("WAITING_MSG:Waiting for " + (playRoomPlayers.size() - readyPlayers.size()) + " more player(s) to ready.");
            }
        }
    }

    private static void startPlayRoomCountdown() {
        countdownRunning = true;
        countdownSeconds = 10;
        if (playRoomTimer != null) playRoomTimer.cancel();
        playRoomTimer = new Timer();
        playRoomTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (countdownSeconds > 0) {
                    broadcast("COUNTDOWN:" + countdownSeconds);
                    System.out.println("Countdown: " + countdownSeconds);
                    countdownSeconds--;
                } else {
                    playRoomTimer.cancel();
                    playRoomTimer = null;
                    countdownRunning = false;
                    startGame();
                }
            }
        }, 1000, 1000);
        System.out.println("Countdown started for " + playRoomPlayers.size() + " players");
    }

    public static synchronized void startGame() {
        if (gameInProgress) return;
        if (playRoomPlayers.size() < 2) {
            broadcast("WAITING_MSG:Not enough players to start game. Need at least 2.");
            return;
        }
        gameInProgress = true;
        currentLevel = 1;
        for (String p : playRoomPlayers) {
            playerScores.putIfAbsent(p, 0);
        }
        broadcast("GAME_STARTED");
        broadcast("NEXT_LEVEL:1");
        broadcastScores();
        startLevelTimer();
        System.out.println("Game started with " + playRoomPlayers.size() + " players.");
    }

    private static void startLevelTimer() {
        if (gameTimer != null) gameTimer.cancel();
        gameTimer = new Timer();
        final int[] timeLeft = {60};
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                timeLeft[0]--;
                broadcast("TIME:" + timeLeft[0]);
                if (timeLeft[0] <= 0) {
                    gameTimer.cancel();
                    broadcast("TIME_UP_NO_WINNER");
                    nextLevel();
                }
            }
        }, 1000, 1000);
    }

    public static synchronized void checkAnswer(String name, String ans) {
        if (!gameInProgress || !playRoomPlayers.contains(name)) return;
        String correct = ANSWERS.get(currentLevel);
        if (correct != null && ans.equalsIgnoreCase(correct)) {
            int newScore = playerScores.getOrDefault(name, 0) + 10;
            playerScores.put(name, newScore);
            broadcast("WINNER:" + name);
            broadcastScores();
            nextLevel();
            System.out.println(name + " answered correctly! +10 points");
        }
    }

    public static synchronized void nextLevel() {
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        currentLevel++;
        if (currentLevel > ANSWERS.size()) {
            endGame();
        } else {
            broadcast("NEXT_LEVEL:" + currentLevel);
            startLevelTimer();
        }
    }

    private static void endGame() {
        gameInProgress = false;
        broadcast("FINAL");
        broadcastScores();
        if (!playerScores.isEmpty()) {
            String winner = Collections.max(playerScores.entrySet(), Map.Entry.comparingByValue()).getKey();
            int highScore = playerScores.get(winner);
            broadcast("WAITING_MSG:Game Over! Winner: " + winner + " with " + highScore + " points");
            System.out.println("Game Ended - Winner: " + winner + " (" + highScore + " pts)");
        }
        resetForNewGame();
    }

    private static void resetForNewGame() {
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        if (playRoomTimer != null) {
            playRoomTimer.cancel();
            playRoomTimer = null;
        }
        countdownRunning = false;
        readyPlayers.clear();
        
        // حفظ النقاط للمستخدمين ولكن إعادة اللاعبين إلى Connection Room
        playRoomPlayers.clear();
        
        broadcastPlayRoomList();
        broadcastConnectionList();
        broadcast("WAITING_MSG:Game ended. Click 'Join Play Room' to play again.");
        System.out.println("Server reset. Ready for new game.\n");
    }

    public static synchronized void removeClient(String name) {
        if (name == null) return;
        allClients.remove(name);
        playRoomPlayers.remove(name);
        readyPlayers.remove(name);
        playerScores.remove(name);
        broadcastConnectionList();
        broadcastPlayRoomList();
        broadcast("PLAYER_LEFT:" + name);
        System.out.println("- " + name + " (Remaining: " + allClients.size() + ")");
        
        if (gameInProgress && playRoomPlayers.size() < 2) {
            endGame();
        } else if (!gameInProgress && playRoomPlayers.size() < 2 && playRoomTimer != null) {
            playRoomTimer.cancel();
            playRoomTimer = null;
            countdownRunning = false;
            broadcast("WAITING_MSG:Need at least 2 players in Play Room to start.");
        } else if (!gameInProgress && playRoomPlayers.size() >= 2 && readyPlayers.size() == playRoomPlayers.size() && !countdownRunning && playRoomTimer == null) {
            startPlayRoomCountdown();
        }
    }

    public static synchronized boolean isGameInProgress() {
        return gameInProgress;
    }

    private static void broadcastConnectionList() {
        StringBuilder sb = new StringBuilder("CONNECTION_USERS:");
        for (String name : allClients.keySet()) {
            sb.append(name).append(",");
        }
        broadcast(sb.toString());
    }

    private static void broadcastPlayRoomList() {
        StringBuilder sb = new StringBuilder("PLAYROOM_USERS:");
        for (String name : playRoomPlayers) {
            sb.append(name).append(",");
        }
        broadcast(sb.toString());
    }

    private static void broadcastScores() {
        StringBuilder sb = new StringBuilder("SCORES:");
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        for (var e : sorted) {
            sb.append(e.getKey()).append(" - ").append(e.getValue()).append(",");
        }
        broadcast(sb.toString());
    }

    private static void broadcast(String msg) {
        synchronized (allClients) {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, NewClientHandler> entry : allClients.entrySet()) {
                if (!entry.getValue().send(msg)) {
                    toRemove.add(entry.getKey());
                }
            }
            for (String name : toRemove) {
                removeClient(name);
            }
        }
    }
}

class NewClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String name;
    private boolean connected = true;

    public NewClientHandler(Socket s) { 
        this.socket = s; 
    }

    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            name = in.readLine();
            if (name == null || name.isEmpty()) {
                socket.close();
                return;
            }
            NewServer.addClient(name, this);

            String line;
            while ((line = in.readLine()) != null && connected) {
                if (line.equals("JOIN_PLAYROOM")) {
                    NewServer.joinPlayRoom(name);
                } else if (line.equals("LEAVE_PLAYROOM")) {
                    NewServer.leavePlayRoom(name);
                } else if (line.equals("READY")) {
                    NewServer.playerReady(name);
                } else if (line.equals("LEAVE_GAME")) {
                    NewServer.removeClient(name);
                    send("GAME_LEFT");
                    break;
                } else if (line.equals("FORCE_START")) {
                    if (!NewServer.isGameInProgress()) {
                        NewServer.startGame();
                    }
                } else if (line.equals("NEXT_ROUND")) {
                    NewServer.nextLevel();
                } else if (line.startsWith("ANSWER:")) {
                    NewServer.checkAnswer(name, line.substring(7));
                } else if (line.equals("RETURN_TO_CONNECTION")) {
                    // العودة إلى Connection Room
                }
            }
        } catch (Exception e) {
            System.out.println("Connection error with client: " + name);
        } finally {
            NewServer.removeClient(name);
            try { socket.close(); } catch (Exception e) {}
        }
    }

    public boolean send(String msg) {
        if (connected && out != null) {
            try {
                out.println(msg);
                return true;
            } catch (Exception e) {
                connected = false;
                return false;
            }
        }
        return false;
    }

    public String getName() { 
        return name; 
    }
}