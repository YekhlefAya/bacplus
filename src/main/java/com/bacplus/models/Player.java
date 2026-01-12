package com.bacplus.models;

import javax.persistence.*;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private int points;

    private String themePref; // "light" or "dark"
    private String langPref; // "fr" or "en"

    // key API key custom
    private String deepSeekKey;

    public Player() {
        this.points = 0;
        this.themePref = "light";
        this.langPref = "fr";
        this.deepSeekKey = "sk-0936c649234349f38f085c9122e663c6"; // Default provided key
    }

    public Player(String username) {
        this();
        this.username = username;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getThemePref() {
        return themePref;
    }

    public void setThemePref(String themePref) {
        this.themePref = themePref;
    }

    public String getLangPref() {
        return langPref;
    }

    public void setLangPref(String langPref) {
        this.langPref = langPref;
    }

    public String getDeepSeekKey() {
        return deepSeekKey;
    }

    public void setDeepSeekKey(String deepSeekKey) {
        this.deepSeekKey = deepSeekKey;
    }
}
