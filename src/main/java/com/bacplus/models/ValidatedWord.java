package com.bacplus.models;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "validated_words")
public class ValidatedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String categoryName;

    private boolean isValid;

    private LocalDateTime checkedAt;

    private String source; // "DEEPSEEK", "DICTIONARY", "CACHE"

    @Column(length = 5)
    private String language; // "fr", "en"

    public ValidatedWord() {
        this.checkedAt = LocalDateTime.now();
    }

    public ValidatedWord(String word, String categoryName, boolean isValid, String source, String language) {
        this();
        this.word = word.toLowerCase().trim();
        this.categoryName = categoryName.toLowerCase().trim();
        this.isValid = isValid;
        this.source = source;
        this.language = language != null ? language.toLowerCase().trim() : "fr";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
