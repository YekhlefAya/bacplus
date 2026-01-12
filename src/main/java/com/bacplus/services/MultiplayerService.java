package com.bacplus.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MultiplayerService {

    private static final int PORT = 5555;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isHost = false;

    // UI Callbacks
    private final StringProperty lastMessage = new SimpleStringProperty();
    private final List<PrintWriter> clientWriters = new ArrayList<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // HOST
    public void startHost() {
        isHost = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                log("Server started on port " + PORT);
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    log("Client connected: " + client.getInetAddress());
                    PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                    synchronized (clientWriters) {
                        clientWriters.add(writer);
                    }
                    new Thread(() -> handleClient(client, writer)).start();
                }
            } catch (IOException e) {
                log("Server Error: " + e.getMessage());
            }
        }).start();
    }

    // JOIN
    public void connect(String ip) {
        isHost = false;
        new Thread(() -> {
            try {
                clientSocket = new Socket(ip, PORT);
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                log("Connected to server: " + ip);

                // Listen loop
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    processMessage(inputLine);
                }
            } catch (IOException e) {
                log("Connection Failed: " + e.getMessage());
            }
        }).start();
    }

    public void sendMessage(String type, Map<String, Object> data) {
        try {
            data.put("type", type);
            String json = mapper.writeValueAsString(data);

            if (isHost) {
                // Broadcast to all
                broadcast(json);
            } else if (out != null) {
                out.println(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcast(String message) {
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(message);
            }
        }
        // Host also processes it
        processMessage(message);
    }

    private void handleClient(Socket socket, PrintWriter writer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Server rebroadcasts
                broadcast(line);
            }
        } catch (IOException e) {
            log("Client disconnected");
        }
    }

    private void processMessage(String json) {
        Platform.runLater(() -> {
            lastMessage.set(json); // Controller will listen to this
            log("Received: " + json);
        });
    }

    private void log(String msg) {
        System.out.println("[MULTI] " + msg);
    }

    public StringProperty lastMessageProperty() {
        return lastMessage;
    }

    public void close() {
        try {
            if (serverSocket != null)
                serverSocket.close();
            if (clientSocket != null)
                clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
