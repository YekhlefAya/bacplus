package com.bacplus.network;

import com.bacplus.services.DeepSeekService.ValidationResult;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * Client for joining multiplayer Baccalauréat games
 */
public class GameClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerName;
    private ClientCallback callback;
    private boolean connected;

    public interface ClientCallback {
        void onConnected();

        void onDisconnected(String reason);

        void onPlayerListUpdate(List<String> players);

        void onGameStart(String letter, String language, List<String> categories, boolean timerEnabled);

        void onSettingsUpdate(boolean timerEnabled, List<String> categories);

        void onWordValidated(String player, String category, String word, boolean valid, int score);

        void onScoreUpdate(Map<String, Integer> scores);

        void onGameEnd(Map<String, Integer> rankings, Map<String, Map<String, String>> playerWords,
                Map<String, Map<String, ValidationResult>> playerValidations);

        void onError(String error);
    }

    public GameClient(String playerName, ClientCallback callback) {
        this.playerName = playerName;
        this.callback = callback;
        this.connected = false;
    }

    /**
     * Connect to the server
     */
    public void connect(String serverIp, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(serverIp, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Send CONNECT message
                GameMessage connectMsg = new GameMessage(GameMessage.MessageType.CONNECT, playerName);
                send(connectMsg);

                connected = true;
                System.out.println("[CLIENT] Connected to server: " + serverIp + ":" + port);

                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onConnected();
                });

                // Listen for messages
                listen();

            } catch (IOException e) {
                System.err.println("[CLIENT] Connection error: " + e.getMessage());
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onError("Impossible de se connecter: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Listen for messages from server
     */
    private void listen() {
        try {
            while (connected) {
                GameMessage message = (GameMessage) in.readObject();
                handleMessage(message);
            }
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                System.err.println("[CLIENT] Connection lost: " + e.getMessage());
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onDisconnected("Connexion perdue");
                });
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Handle incoming messages from server
     */
    private void handleMessage(GameMessage message) {
        System.out.println(
                "[CLIENT] Received: " + message.getType() + " (Data keys: " + message.getData().keySet() + ")");

        switch (message.getType()) {
            case PLAYER_LIST:
                List<String> players = message.getList("players");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onPlayerListUpdate(players);
                });
                break;

            case GAME_START:
                String letter = message.getString("letter");
                String language = message.getString("language");
                List<String> categories = message.getList("categories");
                boolean timerEnabled = message.getBoolean("timerEnabled");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onGameStart(letter, language, categories, timerEnabled);
                });
                break;

            case SETTINGS_UPDATE:
                boolean sTimerEnabled = message.getBoolean("timerEnabled");
                List<String> sCategories = message.getList("categories");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onSettingsUpdate(sTimerEnabled, sCategories);
                });
                break;

            case WORD_VALIDATED:
                String player = message.getPlayerName();
                String category = message.getString("category");
                String word = message.getString("word");
                Boolean valid = (Boolean) message.get("valid");
                Integer score = message.getInt("score");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onWordValidated(player, category, word, valid, score);
                });
                break;

            case SCORE_UPDATE:
                Map<String, Integer> scores = message.getScoreMap("scores");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onScoreUpdate(scores);
                });
                break;

            case GAME_END:
                Map<String, Integer> rankings = message.getScoreMap("rankings");
                Map<String, Map<String, String>> playerWords = (Map<String, Map<String, String>>) message
                        .get("playerWords");
                Map<String, Map<String, ValidationResult>> playerValidations = (Map<String, Map<String, ValidationResult>>) message
                        .get("playerValidations");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onGameEnd(rankings, playerWords, playerValidations);
                });
                break;

            case DISCONNECT:
                String reason = message.getString("reason");
                Platform.runLater(() -> {
                    if (callback != null)
                        callback.onDisconnected(reason != null ? reason : "Déconnecté");
                });
                disconnect();
                break;

            default:
                System.out.println("[CLIENT] Unknown message type: " + message.getType());
        }
    }

    /**
     * Submit a word for validation
     */
    public void submitWord(String category, String word) {
        if (!connected) {
            System.err.println("[CLIENT] Not connected to server");
            return;
        }

        GameMessage msg = new GameMessage(GameMessage.MessageType.WORD_SUBMIT, playerName);
        msg.put("category", category);
        msg.put("word", word);
        send(msg);
    }

    /**
     * Send a message to the server
     */
    private void send(GameMessage message) {
        try {
            out.writeObject(message);
            out.reset(); // CRITICAL: Bypass caching
            out.flush();
            System.out.println("[CLIENT] Message sent: " + message.getType());
        } catch (IOException e) {
            System.err.println("[CLIENT] Error sending message: " + e.getMessage());
        }
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        connected = false;
        try {
            if (out != null) {
                GameMessage msg = new GameMessage(GameMessage.MessageType.DISCONNECT, playerName);
                send(msg);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[CLIENT] Disconnected");
        } catch (IOException e) {
            System.err.println("[CLIENT] Error disconnecting: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getPlayerName() {
        return playerName;
    }
}
