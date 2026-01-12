package com.bacplus.models;

import javax.persistence.*;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private boolean isSystem; // If true, cannot be deleted/edited
    private boolean isMandatory; // If true, never deactivated (Pays, Ville)
    private boolean isActive;

    private int wordCount;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public Category() {
        this.createdAt = java.time.LocalDateTime.now();
    }

    public Category(String name, boolean isSystem, boolean isMandatory) {
        this.name = name;
        this.isSystem = isSystem;
        this.isMandatory = isMandatory;
        this.isActive = true;
        this.wordCount = 0;
        this.createdAt = java.time.LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public void setSystem(boolean system) {
        isSystem = system;
    }

    public boolean isMandatory() {
        return isMandatory;
    }

    public void setMandatory(boolean mandatory) {
        isMandatory = mandatory;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
