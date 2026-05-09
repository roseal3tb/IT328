import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Timer;

public class NewServer {
    private static final Map<String, NewClientHandler> allClients = new LinkedHashMap<>();
    private static final Set<String> playRoomPlayers = new LinkedHashSet<>();
    private static final Set<String> readyPlayers = new HashSet<>();
    private static final Set<String> inGamePlayers = new LinkedHashSet<>();
    private static final Map<String, Integer> playerScores = new HashMap<>();
    private static final int MAX_PLAYROOM = 4;

    private static boolean gameInProgress = false;
    private static int currentLevelIndex = 0;
    private static Timer gameTimer;
    private static Timer playRoomTimer;
    private static boolean countdownRunning = false;
    private static int countdownSeconds = 10;
    
    // Timer for auto-start after 30 seconds
    private static Timer autoStartTimer = null;
    
    private static final List<Integer> availableLevels = new ArrayList<>();
    private static final Random random = new Random();

    private static final Map<Integer, String> ANSWERS = Map.of(
        1, "mac", 2, "sephora", 3, "huda beauty",
        4, "fenty beauty", 5, "dior", 6, "rare beauty"
    );

    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(4000);
        System.out.println("Glam & Ping Server Started");
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
        System.out.println("+ " + name + " joined (Total: " + allClients.size() + ")");
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
        }
        
        // Cancel any existing auto-start timer
        if (autoStartTimer != null) {
            autoStartTimer.cancel();
            autoStartTimer = null;
        }
        
        // Start 30-second timer if we have at least 2 players and game not started
        if (!gameInProgress && playRoomPlayers.size() >= 2) {
            autoStartTimer = new Timer();
            autoStartTimer.schedule(new TimerTask() {
                public void run() {
                    synchronized (NewServer.class) {
                        // Only start if game still not in progress and we have 2+ players
                        if (!gameInProgress && playRoomPlayers.size() >= 2) {
                            System.out.println("30 seconds passed! Starting game automatically...");
                            broadcast("WAITING_MSG:30 seconds passed! Starting game now...");
                            startGame();
                        }
                        autoStartTimer = null;
                    }
                }
            }, 30000);
            broadcast("WAITING_MSG:Game will start automatically in 30 seconds. Press READY to start sooner.");
        }
    }

    public static synchronized void leavePlayRoom(String name) {
        if (gameInProgress) return;
        playRoomPlayers.remove(name);
        readyPlayers.remove(name);
        broadcastPlayRoomList();
        broadcastConnectionList();
        broadcast("REMOVE_PLAYER:" + name);
        broadcast("PLAYER_LEFT:" + name);
        System.out.println(name + " left Play Room");
        
        // Cancel auto-start if less than 2 players remain
        if (playRoomPlayers.size() < 2 && autoStartTimer != null) {
            autoStartTimer.cancel();
            autoStartTimer = null;
            broadcast("WAITING_MSG:Need at least 2 players to start game.");
        }
        
        if (playRoomPlayers.size() < 2 && playRoomTimer != null) {
            playRoomTimer.cancel();
            playRoomTimer = null;
            countdownRunning = false;
            broadcast("WAITING_MSG:Need at least 2 players to start.");
        }
        if (playRoomPlayers.size() >= 2 && readyPlayers.size() == playRoomPlayers.size() && !countdownRunning && playRoomTimer == null) {
            // Cancel auto-start if all ready
            if (autoStartTimer != null) {
                autoStartTimer.cancel();
                autoStartTimer = null;
            }
            startPlayRoomCountdown();
        }
    }

    public static synchronized void playerReady(String name) {
        if (!playRoomPlayers.contains(name)) return;
        if (gameInProgress) return;
        if (!readyPlayers.contains(name)) {
            readyPlayers.add(name);
            broadcastPlayRoomList();
            broadcast("WAITING_MSG:" + name + " is ready (" + readyPlayers.size() + "/" + playRoomPlayers.size() + ")");
            
            // If all players are ready, cancel auto-start and start countdown
            if (readyPlayers.size() == playRoomPlayers.size() && playRoomPlayers.size() >= 2 && !countdownRunning && playRoomTimer == null) {
                if (autoStartTimer != null) {
                    autoStartTimer.cancel();
                    autoStartTimer = null;
                    broadcast("WAITING_MSG:All players ready! Starting game...");
                }
                startPlayRoomCountdown();
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
                    countdownSeconds--;
                } else {
                    playRoomTimer.cancel();
                    playRoomTimer = null;
                    countdownRunning = false;
                    startGame();
                }
            }
        }, 1000, 1000);
    }

    public static synchronized void startGame() {
        if (gameInProgress) return;
        if (playRoomPlayers.size() < 2) return;
        
        // Cancel auto-start if it's running
        if (autoStartTimer != null) {
            autoStartTimer.cancel();
            autoStartTimer = null;
        }
        
        // Cancel countdown timer if it's running
        if (playRoomTimer != null) {
            playRoomTimer.cancel();
            playRoomTimer = null;
        }
        countdownRunning = false;
        
        // If no one is ready, mark all as ready
        if (readyPlayers.isEmpty()) {
            readyPlayers.addAll(playRoomPlayers);
            broadcast("WAITING_MSG:Auto-starting game with all players!");
        }
        
        inGamePlayers.clear();
        inGamePlayers.addAll(readyPlayers);
        
        if (inGamePlayers.size() < 2) return;
        
        gameInProgress = true;
        
        availableLevels.clear();
        availableLevels.addAll(ANSWERS.keySet());
        Collections.shuffle(availableLevels, random);
        currentLevelIndex = 0;
        
        for (String p : inGamePlayers) {
            playerScores.put(p, 0);
        }
        
        broadcast("GAME_STARTED");
        broadcast("NEXT_LEVEL:" + availableLevels.get(currentLevelIndex));
        broadcastScores();
        startLevelTimer();
        
        List<String> toRemove = new ArrayList<>();
        for (String p : playRoomPlayers) {
            if (!inGamePlayers.contains(p)) {
                NewClientHandler h = allClients.get(p);
                if (h != null) h.send("NOT_READY_GAME_OVER");
                toRemove.add(p);
            }
        }
        for (String p : toRemove) {
            playRoomPlayers.remove(p);
            readyPlayers.remove(p);
        }
        broadcastPlayRoomList();
        broadcastConnectionList();
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
                    gameTimer = null;
                    broadcast("TIME_UP_NO_WINNER");
                    if (gameInProgress) goToNextLevel();
                }
            }
        }, 1000, 1000);
    }
    
    public static synchronized void goToNextLevel() {
        if (!gameInProgress) return;
        
        currentLevelIndex++;
        
        if (currentLevelIndex >= availableLevels.size()) {
            endGame();
        } else {
            broadcast("NEXT_LEVEL:" + availableLevels.get(currentLevelIndex));
            startLevelTimer();
        }
    }

    public static synchronized void checkAnswer(String name, String ans) {
        if (!gameInProgress) return;
        if (!inGamePlayers.contains(name)) return;
        
        int currentLevel = availableLevels.get(currentLevelIndex);
        String correct = ANSWERS.get(currentLevel);
        if (correct != null && ans.equalsIgnoreCase(correct)) {
            int newScore = playerScores.getOrDefault(name, 0) + 10;
            playerScores.put(name, newScore);
            broadcast("WINNER:" + name);
            broadcastScores();
            nextLevel();
        }
    }
    
    public static synchronized void nextLevel() {
        if (!gameInProgress) return;
        if (gameTimer != null) gameTimer.cancel();
        goToNextLevel();
    }

    private static void endGame() {
        gameInProgress = false;
        
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        
        if (!inGamePlayers.isEmpty()) {
            String winner = null;
            int highestScore = -1;
            for (String p : inGamePlayers) {
                int score = playerScores.getOrDefault(p, 0);
                if (score > highestScore) {
                    highestScore = score;
                    winner = p;
                }
            }
            
            if (winner != null && highestScore > 0) {
                broadcast("WINNER_ANNOUNCEMENT:" + winner + ":" + highestScore);
            } else {
                broadcast("WINNER_ANNOUNCEMENT:NONE:0");
            }
        } else {
            broadcast("WINNER_ANNOUNCEMENT:NONE:0");
        }
        
        resetForNewGame();
    }

    private static void resetForNewGame() {
        if (autoStartTimer != null) {
            autoStartTimer.cancel();
            autoStartTimer = null;
        }
        
        if (gameTimer != null) gameTimer.cancel();
        if (playRoomTimer != null) playRoomTimer.cancel();
        countdownRunning = false;
        readyPlayers.clear();
        inGamePlayers.clear();
        playRoomPlayers.clear();
        
        broadcastPlayRoomList();
        broadcastConnectionList();
        broadcast("WAITING_MSG:Game ended. Join Play Room to play again.");
    }

    public static synchronized void removeClient(String name) {
        if (name == null) return;
        
        boolean wasInGame = inGamePlayers.contains(name);
        
        // إزالة من جميع القوائم
        allClients.remove(name);
        playRoomPlayers.remove(name);
        readyPlayers.remove(name);
        inGamePlayers.remove(name);
        playerScores.remove(name);
        
        // Cancel auto-start if less than 2 players remain
        if (!gameInProgress && playRoomPlayers.size() < 2 && autoStartTimer != null) {
            autoStartTimer.cancel();
            autoStartTimer = null;
        }
        
        // إرسال أمر الإزالة المباشر
        broadcast("REMOVE_PLAYER:" + name);
        
        // تحديث القوائم
        broadcastConnectionList();
        broadcastPlayRoomList();
        broadcast("PLAYER_LEFT:" + name);
        
        System.out.println("- " + name + " removed. Remaining: " + allClients.size());
        
        if (wasInGame) {
            broadcastScores();
            broadcast("WAITING_MSG:" + name + " left the game.");
        }
        
        if (gameInProgress) {
            if (inGamePlayers.isEmpty()) {
                endGame();
            } else if (inGamePlayers.size() == 1) {
                String winner = inGamePlayers.iterator().next();
                int finalScore = playerScores.getOrDefault(winner, 0);
                broadcast("WINNER_ANNOUNCEMENT:" + winner + ":" + finalScore);
                broadcast("WAITING_MSG:" + winner + " wins!");
                endGame();
            }
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
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        
        for (String p : inGamePlayers) {
            sorted.add(new java.util.AbstractMap.SimpleEntry<>(p, playerScores.getOrDefault(p, 0)));
        }
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
                    if (!NewServer.isGameInProgress()) NewServer.startGame();
                } else if (line.startsWith("ANSWER:")) {
                    NewServer.checkAnswer(name, line.substring(7));
                }
            }
        } catch (Exception e) {
            System.out.println("Error with client: " + name);
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
}