package com.bacplus.network;

import com.bacplus.services.DeepSeekService;
import com.bacplus.services.DeepSeekService.ValidationResult;
import javafx.application.Platform;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server for hosting multiplayer Baccalauréat games
 */
public class GameServer {
    private static final int PORT = 8888;

    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Integer> playerScores;
    private boolean gameStarted;
    private String currentLetter;
    private String gameLanguage;
    private List<String> activeCategories;
    private boolean timerEnabled;
    private Map<String, Map<String, String>> playerWords;
    private Map<String, Map<String, ValidationResult>> playerValidations;
    private DeepSeekService deepSeekService;
    private ServerCallback callback;

    public interface ServerCallback {
        void onPlayerJoined(String playerName);

        void onPlayerLeft(String playerName);

        void onError(String error);

        void onServerStarted();
    }

    public GameServer(ServerCallback callback) {
        this.clients = new ConcurrentHashMap<>();
        this.playerScores = new ConcurrentHashMap<>();
        this.playerWords = new ConcurrentHashMap<>();
        this.playerValidations = new ConcurrentHashMap<>();
        this.gameStarted = false;
        this.callback = callback;
        this.deepSeekService = new DeepSeekService();
        this.activeCategories = new ArrayList<>();
        this.timerEnabled = false;
    }

    /**
     * Start the server on the default port
     */
    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("[SERVER] Started on port " + PORT);

                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onServerStarted();
                });

                // Accept client connections
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] New client connected: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[SERVER] Error: " + e.getMessage());
                    Platform.runLater(() -> {
                        if (callback != null)
                            callback.onError("Erreur serveur: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    /**
     * Start the game with given parameters
     */
    public void startGame(String letter, String language, List<String> categories, boolean timerEnabled) {
        this.currentLetter = letter;
        this.gameLanguage = language;
        this.activeCategories = categories;
        this.timerEnabled = timerEnabled;
        this.gameStarted = true;

        // Initialize structures
        for (String player : clients.keySet()) {
            playerScores.put(player, 0);
            playerWords.put(player, new ConcurrentHashMap<>());
            playerValidations.put(player, new ConcurrentHashMap<>());
        }

        // Send GAME_START message to all clients
        GameMessage msg = new GameMessage(GameMessage.MessageType.GAME_START);
        msg.put("letter", letter);
        msg.put("language", language);
        msg.put("categories", categories);
        msg.put("timerEnabled", timerEnabled);
        broadcast(msg);

        System.out.println("[SERVER] Game started with letter: " + letter + ", Timer: " + timerEnabled);
    }

    /**
     * End the game and send final rankings
     */
    public void endGame() {
        gameStarted = false;

        // Sort players by score
        List<Map.Entry<String, Integer>> rankingsList = new ArrayList<>(playerScores.entrySet());
        rankingsList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        GameMessage msg = new GameMessage(GameMessage.MessageType.GAME_END);
        msg.put("rankings", new HashMap<>(playerScores));
        msg.put("playerWords", new HashMap<>(playerWords));
        msg.put("playerValidations", new HashMap<>(playerValidations));
        broadcast(msg);

        System.out.println("[SERVER] Game ended. Rankings: " + rankingsList);
    }

    /**
     * Update game settings and broadcast to all clients
     */
    public void updateSettings(boolean timerEnabled, List<String> categories) {
        this.timerEnabled = timerEnabled;
        this.activeCategories = categories;

        GameMessage msg = new GameMessage(GameMessage.MessageType.SETTINGS_UPDATE);
        msg.put("timerEnabled", timerEnabled);
        msg.put("categories", categories);
        broadcast(msg);
        System.out.println("[SERVER] Settings updated: Timer=" + timerEnabled + ", Cats=" + categories.size());
    }

    /**
     * Broadcast a message to all connected clients
     */
    private void broadcast(GameMessage message) {
        System.out.println("[SERVER] Broadcasting: " + message.getType() + " to " + clients.size() + " clients");
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    /**
     * Stop the server
     */
    public void stop() {
        try {
            // Notify all clients
            GameMessage msg = new GameMessage(GameMessage.MessageType.DISCONNECT);
            msg.put("reason", "Server closed");
            broadcast(msg);

            // Close all client connections
            for (ClientHandler client : clients.values()) {
                client.close();
            }
            clients.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("[SERVER] Stopped");
        } catch (IOException e) {
            System.err.println("[SERVER] Error stopping: " + e.getMessage());
        }
    }

    public List<String> getConnectedPlayers() {
        return new ArrayList<>(clients.keySet());
    }

    public int getPlayerCount() {
        return clients.size();
    }

    /**
     * Handler for individual client connections
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Wait for CONNECT message
                GameMessage connectMsg = (GameMessage) in.readObject();
                if (connectMsg.getType() == GameMessage.MessageType.CONNECT) {
                    playerName = connectMsg.getPlayerName();

                    // Force unique player names
                    String originalName = playerName;
                    int count = 1;
                    while (clients.containsKey(playerName)) {
                        playerName = originalName + "(" + count++ + ")";
                    }

                    clients.put(playerName, this);
                    System.out.println("[SERVER] Player joined: " + playerName);

                    // Send initial settings to the new client
                    GameMessage settingsMsg = new GameMessage(GameMessage.MessageType.SETTINGS_UPDATE);
                    settingsMsg.put("timerEnabled", timerEnabled);
                    settingsMsg.put("categories", activeCategories);
                    send(settingsMsg);
                    System.out.println("[SERVER] Sent initial settings to " + playerName + ": Timer=" + timerEnabled
                            + ", Cats=" + (activeCategories != null ? activeCategories.size() : 0));
                    Platform.runLater(() -> {
                        if (callback != null)
                            callback.onPlayerJoined(playerName);
                    });

                    // Send current player list to all clients
                    GameMessage playerListMsg = new GameMessage(GameMessage.MessageType.PLAYER_LIST);
                    playerListMsg.put("players", getConnectedPlayers());
                    broadcast(playerListMsg);
                }

                // Listen for messages
                while (true) {
                    GameMessage message = (GameMessage) in.readObject();
                    handleMessage(message);
                }

            } catch (IOException | ClassNotFoundException e) {
                System.out.println("[SERVER] Client disconnected: " + playerName);
            } finally {
                if (playerName != null) {
                    clients.remove(playerName);
                    playerScores.remove(playerName);

                    Platform.runLater(() -> {
                        if (callback != null)
                            callback.onPlayerLeft(playerName);
                    });

                    // Notify other clients
                    GameMessage playerListMsg = new GameMessage(GameMessage.MessageType.PLAYER_LIST);
                    playerListMsg.put("players", getConnectedPlayers());
                    broadcast(playerListMsg);
                }
                close();
            }
        }

        private void handleMessage(GameMessage message) {
            switch (message.getType()) {
                case WORD_SUBMIT:
                    handleWordSubmit(message);
                    break;
                case DISCONNECT:
                    close();
                    break;
                default:
                    System.out.println("[SERVER] Received: " + message);
            }
        }

        private void handleWordSubmit(GameMessage message) {
            String category = message.getString("category");
            String word = message.getString("word");
            String player = message.getPlayerName();

            System.out.println("[SERVER] Validating word: " + word + " for " + category + " by " + player);

            // Validate word using DeepSeekService
            ValidationResult result = deepSeekService.validateWord(category, word, currentLetter, gameLanguage);

            // Record word and update score
            Map<String, String> words = playerWords.get(player);
            if (words != null)
                words.put(category, word);

            Map<String, ValidationResult> validations = playerValidations.get(player);
            if (validations != null)
                validations.put(category, result);

            int currentScore = playerScores.getOrDefault(player, 0);
            if (result.isValid) {
                playerScores.put(player, currentScore + result.score);
            }

            // Broadcast validation result
            GameMessage validationMsg = new GameMessage(GameMessage.MessageType.WORD_VALIDATED);
            validationMsg.setPlayerName(player);
            validationMsg.put("category", category);
            validationMsg.put("word", word);
            validationMsg.put("valid", result.isValid);
            validationMsg.put("score", result.score);
            broadcast(validationMsg);

            // Broadcast score update
            GameMessage scoreMsg = new GameMessage(GameMessage.MessageType.SCORE_UPDATE);
            scoreMsg.put("scores", new HashMap<>(playerScores));
            broadcast(scoreMsg);
        }

        public void send(GameMessage message) {
            try {
                out.writeObject(message);
                out.reset(); // CRITICAL: Bypass caching for modified objects
                out.flush();
                System.out.println("[SERVER] Message sent to " + playerName + ": " + message.getType());
            } catch (IOException e) {
                System.err.println("[SERVER] Error sending to " + playerName + ": " + e.getMessage());
            }
        }

        public void close() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("[SERVER] Error closing client: " + e.getMessage());
            }
        }
    }
}
