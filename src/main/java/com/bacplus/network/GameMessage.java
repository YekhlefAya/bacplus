package com.bacplus.network;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all network messages exchanged between server and clients
 */
public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        CONNECT, // Player joins game
        PLAYER_LIST, // Server sends current player list
        GAME_START, // Server starts the game
        WORD_SUBMIT, // Player submits a word
        WORD_VALIDATED, // Server validates and broadcasts word
        SCORE_UPDATE, // Server broadcasts score changes
        GAME_END, // Server ends game and sends final rankings
        DISCONNECT, // Player leaves
        SETTINGS_UPDATE, // Host updates game settings (timer, categories)
        ERROR // Error message
    }

    private MessageType type;
    private String playerName;
    private Map<String, Object> data;

    public GameMessage(MessageType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    public GameMessage(MessageType type, String playerName) {
        this.type = type;
        this.playerName = playerName;
        this.data = new HashMap<>();
    }

    // Getters and Setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    // Convenience methods for data
    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getInt(String key) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return null;
    }

    public Boolean getBoolean(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getScoreMap(String key) {
        Object value = data.get(key);
        if (value instanceof Map) {
            return (Map<String, Integer>) value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "GameMessage{type=" + type + ", player=" + playerName + ", data=" + data + "}";
    }
}
