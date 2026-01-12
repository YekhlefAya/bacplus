-- Create categories table
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_system BOOLEAN DEFAULT FALSE,
    is_mandatory BOOLEAN DEFAULT FALSE,
    word_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert System Categories if they don't exist
-- We use INSERT IGNORE or checking existence is better handled in Java code for "init" 
-- allowing the unique constraint to block duplicates.
INSERT IGNORE INTO categories (name, is_system, is_active, is_mandatory, word_count) VALUES 
('Pays', TRUE, TRUE, TRUE, 105),
('Ville', TRUE, TRUE, TRUE, 87),
('Animal', TRUE, TRUE, FALSE, 312),
('Métier', TRUE, TRUE, FALSE, 156),
('Marque', TRUE, FALSE, FALSE, 0);

-- Create validated_words table
CREATE TABLE IF NOT EXISTS validated_words (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(100) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    is_valid BOOLEAN NOT NULL,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(50),
    language VARCHAR(5) DEFAULT 'fr'
);
