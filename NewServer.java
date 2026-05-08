import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Timer;

public class NewServer {
    private static final Map<String, NewClientHandler> allClients = new LinkedHashMap<>();
    private static final Set<String> readyPlayers = new HashSet<>();
    private static final HashMap<String, Integer> playerScores = new HashMap<>();
    
    private static int currentLevel = 1;
    private static Timer gameTimer;
    private static Timer waitingRoomTimer;
    private static final int MAX_ROOM_CAPACITY = 4;
    private static boolean countdownRunning = false;
    private static boolean gameInProgress = false;
    private static boolean waitingForPlayers = false;
    private static int waitingCountdown = 30;
    
    private static final Map<Integer, String> ANSWERS = Map.of(
        1, "mac", 2, "sephora", 3, "huda beauty", 
        4, "fenty beauty", 5, "dior", 6, "rare beauty"
    );

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(4000);
        System.out.println("Glam & Ping Server Started on port 4000");
        System.out.println("Maximum capacity: " + MAX_ROOM_CAPACITY + " players");
        System.out.println("Waiting for connections...\n");

        while (true) {
            Socket socket = serverSocket.accept();
            synchronized (allClients) {
                if (allClients.size() >= MAX_ROOM_CAPACITY) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("FULL");
                    socket.close();
                    System.out.println("Connection rejected: Studio full");
                    continue;
                }
            }
            new Thread(new NewClientHandler(socket)).start();
        }
    }

    public static synchronized void addClient(String name, NewClientHandler handler) {
        if (allClients.containsKey(name)) {
            handler.send("ERROR:Name already taken. Please reconnect with a different name.");
            return;
        }
        allClients.put(name, handler);
        playerScores.put(name, 0);
        System.out.println("Player joined: " + name + " (Total: " + allClients.size() + "/" + MAX_ROOM_CAPACITY + ")");
        broadcastUserList();
        broadcast("PLAYER_JOINED:" + name);
        
        if (allClients.size() == 1) {
            broadcast("WAITING_MSG:Waiting for at least 2 players to join... (You are alone)");
        } else if (allClients.size() >= 2 && !gameInProgress && !waitingForPlayers) {
            broadcast("WAITING_MSG:" + name + " has joined the waiting room.");
            startWaitingRoomTimer();
        }
    }
    
    private static void startWaitingRoomTimer() {
        if (waitingRoomTimer != null) {
            waitingRoomTimer.cancel();
        }
        waitingForPlayers = true;
        waitingCountdown = 30;
        
        broadcast("WAITING_MSG:Game will start automatically in 30 seconds!");
        
        waitingRoomTimer = new Timer();
        waitingRoomTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (waitingCountdown > 0 && allClients.size() >= 2 && !gameInProgress) {
                    broadcast("COUNTDOWN:" + waitingCountdown);
                    waitingCountdown--;
                } else if (waitingCountdown <= 0 && allClients.size() >= 2 && !gameInProgress) {
                    waitingRoomTimer.cancel();
                    waitingForPlayers = false;
                    startGame();
                } else if (allClients.size() < 2) {
                    waitingRoomTimer.cancel();
                    waitingForPlayers = false;
                    broadcast("WAITING_MSG:Need at least 2 players to start.");
                }
            }
        }, 1000, 1000);
    }

    public static synchronized void playerReady(String name) {
        if (!gameInProgress && !readyPlayers.contains(name)) {
            readyPlayers.add(name);
            System.out.println(name + " is ready! (" + readyPlayers.size() + "/" + allClients.size() + " ready)");
            broadcast("WAITING_MSG:" + name + " is ready to play. (" + readyPlayers.size() + "/" + allClients.size() + " ready)");
            
            if (readyPlayers.size() >= 2 && readyPlayers.size() == allClients.size() && !countdownRunning && !gameInProgress) {
                if (waitingRoomTimer != null) {
                    waitingRoomTimer.cancel();
                    waitingForPlayers = false;
                }
                startCountdown();
            }
        }
    }

    private static void startCountdown() {
        countdownRunning = true;
        gameInProgress = true;
        System.out.println("All players ready! Starting 10-second countdown...");
        broadcast("WAITING_MSG:All players are ready! Starting 10-second countdown...");
        
        new Thread(() -> {
            for (int i = 10; i >= 0; i--) {
                broadcast("COUNTDOWN:" + i);
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
            startGame();
        }).start();
    }

    public static synchronized void startGame() {
        gameInProgress = true;
        countdownRunning = false;
        
        for (String player : allClients.keySet()) {
            playerScores.putIfAbsent(player, 0);
        }
        
        broadcast("GAME_STARTED");
        broadcast("NEXT_LEVEL:" + currentLevel);
        broadcast("WAITING_MSG:GAME STARTED! Guess the beauty brand!");
        broadcastScores();
        startLevelTimer();
        System.out.println("Game started with " + allClients.size() + " players.");
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
                    broadcast("TIME_UP_NO_WINNER");
                    nextLevel();
                }
            }
        }, 1000, 1000);
    }

    public static synchronized void checkAnswer(String name, String ans) {
        if (!gameInProgress) return;
        
        String correctAnswer = ANSWERS.get(currentLevel);
        if (correctAnswer != null && ans.equalsIgnoreCase(correctAnswer)) {
            int newScore = playerScores.getOrDefault(name, 0) + 10;
            playerScores.put(name, newScore);
            broadcast("WINNER:" + name);
            broadcast("WAITING_MSG:" + name.toUpperCase() + " guessed correctly! +10 points!");
            broadcastScores();
            nextLevel();
            System.out.println(name + " answered correctly! Moving to next level...");
        }
    }

    public static synchronized void nextLevel() {
        if (gameTimer != null) gameTimer.cancel();
        currentLevel++;
        
        if (currentLevel > ANSWERS.size()) {
            endGame();
        } else {
            broadcast("NEXT_LEVEL:" + currentLevel);
            broadcast("WAITING_MSG:Level " + currentLevel + "! New brand to guess!");
            startLevelTimer();
            System.out.println("Moving to Level " + currentLevel);
        }
    }

    private static void endGame() {
        gameInProgress = false;
        broadcast("FINAL");
        broadcastScores();
        
        String winner = Collections.max(playerScores.entrySet(), Map.Entry.comparingByValue()).getKey();
        int highScore = playerScores.get(winner);
        broadcast("WAITING_MSG:GAME OVER! Winner: " + winner + " with " + highScore + " points!");
        
        System.out.println("GAME ENDED - Winner: " + winner + " (" + highScore + " points)");
        resetServer();
    }

    private static void resetServer() {
        if (gameTimer != null) gameTimer.cancel();
        if (waitingRoomTimer != null) waitingRoomTimer.cancel();
        countdownRunning = false;
        gameInProgress = false;
        waitingForPlayers = false;
        readyPlayers.clear();
        currentLevel = 1;
        playerScores.clear();
        
        for (String name : allClients.keySet()) {
            playerScores.put(name, 0);
        }
        
        System.out.println("Server reset. Ready for new game.\n");
        
        if (allClients.size() >= 2) {
            startWaitingRoomTimer();
        }
    }

    public static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USERS:");
        synchronized (allClients) {
            for (String name : allClients.keySet()) {
                sb.append(name).append(",");
            }
        }
        broadcast(sb.toString());
    }

    public static void broadcastScores() {
        StringBuilder sb = new StringBuilder("SCORES:");
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(playerScores.entrySet());
        sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (Map.Entry<String, Integer> entry : sortedScores) {
            sb.append(entry.getKey()).append(" - ").append(entry.getValue()).append(",");
        }
        broadcast(sb.toString());
    }

    public static void broadcast(String msg) {
        synchronized (allClients) {
            List<NewClientHandler> toRemove = new ArrayList<>();
            for (NewClientHandler handler : allClients.values()) {
                if (!handler.send(msg)) {
                    toRemove.add(handler);
                }
            }
            for (NewClientHandler handler : toRemove) {
                String name = handler.getName();
                if (name != null) {
                    allClients.remove(name);
                    readyPlayers.remove(name);
                    playerScores.remove(name);
                    System.out.println("Player disconnected: " + name);
                    broadcast("PLAYER_LEFT:" + name);
                }
            }
            if (!toRemove.isEmpty()) {
                broadcastUserList();
                if (allClients.size() < 2 && gameInProgress) {
                    System.out.println("Not enough players! Resetting game...");
                    resetServer();
                    broadcast("WAITING_MSG:Not enough players! Game reset.");
                } else if (allClients.size() >= 2 && !gameInProgress) {
                    readyPlayers.clear();
                    if (waitingRoomTimer != null) {
                        waitingRoomTimer.cancel();
                    }
                    startWaitingRoomTimer();
                }
            }
        }
    }

    public static synchronized void removeClient(String name) {
        if (name != null) {
            allClients.remove(name);
            readyPlayers.remove(name);
            playerScores.remove(name);
            broadcastUserList();
            broadcast("PLAYER_LEFT:" + name);
            System.out.println("Player left: " + name + " (Remaining: " + allClients.size() + ")");
            
            if (allClients.size() < 2 && gameInProgress) {
                System.out.println("Not enough players! Resetting game...");
                resetServer();
                broadcast("WAITING_MSG:Not enough players! Game reset.");
            } else if (allClients.size() >= 2 && !gameInProgress) {
                readyPlayers.clear();
                if (waitingRoomTimer != null) {
                    waitingRoomTimer.cancel();
                }
                startWaitingRoomTimer();
            } else if (allClients.size() == 1) {
                broadcast("WAITING_MSG:Waiting for at least 2 players to join...");
            }
        }
    }
    
    public static synchronized boolean isGameInProgress() {
        return gameInProgress;
    }
}

class NewClientHandler implements Runnable {
    private Socket s;
    private PrintWriter out;
    private BufferedReader in;
    private String name;
    private boolean connected = true;
    
    public NewClientHandler(Socket s) { 
        this.s = s; 
    }
    
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintWriter(s.getOutputStream(), true);
            name = in.readLine();
            
            if (name != null && !name.isEmpty()) {
                NewServer.addClient(name, this);
            } else {
                return;
            }
            
            String line;
            while ((line = in.readLine()) != null && connected) {
                if (line.equals("READY")) {
                    NewServer.playerReady(name);
                } else if (line.equals("LEAVE")) {
                    break;
                } else if (line.equals("FORCE_START")) {
                    if (!NewServer.isGameInProgress()) {
                        NewServer.startGame();
                    }
                } else if (line.equals("NEXT_ROUND")) {
                    NewServer.nextLevel();
                } else if (line.startsWith("ANSWER:")) {
                    NewServer.checkAnswer(name, line.substring(7));
                }
            }
        } catch (Exception e) {
            System.out.println("Connection error with client: " + name);
        } finally {
            NewServer.removeClient(name);
            try { s.close(); } catch (Exception e) {}
        }
    }
    
    public boolean send(String m) {
        if (connected && out != null) {
            try {
                out.println(m);
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