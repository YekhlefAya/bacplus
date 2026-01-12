package com.bacplus.services;

import com.bacplus.models.Category;
import java.util.List;
import java.util.Random;

public class GameEngine {

    private int score;
    private int timeRemainingSeconds;

    public GameEngine() {
        this.score = 0;
    }

    public int calculateWordScore(String word) {
        if (word == null || word.isEmpty())
            return 0;
        int pts = 2; // base per word
        if (word.length() > 8)
            pts += 5; // length bonus
        return pts;
    }

    public int calculateTotalScore(List<Boolean> validations, boolean allFilled) {
        int total = validations.stream().mapToInt(v -> v ? 1 : 0).sum() * 2; // simplified
        if (allFilled)
            total += 35; // Completion bonus
        return total;
    }

    public char generateRandomLetter() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        // Remove hard letters: K, W, X, Y, Z
        String easyLetters = "ABCDEFGHIJLMNOPQRSTUV";
        // User asked to avoid K,W,X,Y,Z explicitly
        return easyLetters.charAt(new Random().nextInt(easyLetters.length()));
    }
}
