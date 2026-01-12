package com.bacplus;

import com.bacplus.models.Category;
import com.bacplus.models.Game;
import com.bacplus.models.Player;
import com.bacplus.models.ValidatedWord;
import com.bacplus.network.GameClient;
import com.bacplus.network.GameServer;
import com.bacplus.services.DeepSeekService;
import com.bacplus.services.DeepSeekService.ValidationResult;
import com.bacplus.utils.HibernateUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    // --- UI INJECTION ---
    @FXML
    private StackPane rootPane;

    // PANES
    @FXML
    private VBox homePane;
    @FXML
    private VBox setupPane;
    @FXML
    private BorderPane gamePane;
    @FXML
    private VBox resultsPane;
    @FXML
    private VBox categoriesPane;
    @FXML
    private VBox historyPane;
    @FXML
    private VBox settingsPane;

    // HOME
    @FXML
    private Label lblAppTitle, lblAppSubtitle, lblAppTitleLogo;
    @FXML
    private Button btnPlay;
    @FXML
    private Button btnCats;
    @FXML
    private Button btnScores;
    @FXML
    private Button btnConfig;

    // SETUP
    @FXML
    private VBox categoriesSelectionBox;
    @FXML
    private RadioButton radioSolo, radioMulti;
    @FXML
    private ToggleGroup modeGroup;
    @FXML
    private VBox multiplayerConfigBox;
    @FXML
    private TextField serverIpField;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private Button btnBackSetup;
    @FXML
    private Button btnStart;
    @FXML
    private CheckBox cbEnableTimer;
    @FXML
    private Button btnHost;
    @FXML
    private Button btnJoin;

    @FXML
    private Label lblSetupTitle;
    @FXML
    private Label lblGameMode;
    @FXML
    private Label lblSelectCats, lblMinCats;

    @FXML
    private Label gameLetterLabel;
    @FXML
    private Label gameLetterLabelSetup; // Letter display in setup screen
    @FXML
    private Label timerLabel;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label playerPointsLabel; // Badge in Game
    @FXML
    private Label pointsLabel; // Badge in Home
    @FXML
    private GridPane gameGrid;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button btnHint;
    @FXML
    private Button btnQuit;
    @FXML
    private Button btnEnd;

    // RESULTS
    @FXML
    private Label finalLetterLabel;
    @FXML
    private Label finalScoreLabel;
    @FXML
    private VBox resultsListBox;
    @FXML
    private Button btnResultsBack;

    // CATEGORIES MANAGEMENT
    @FXML
    private Label activeCountLabel;
    @FXML
    private ProgressBar activeProgressBar;
    @FXML
    private HBox previewCategoriesBox;
    @FXML
    private ListView<HBox> categoriesList;
    @FXML
    private TextField newCatNameField;
    @FXML
    private Button btnCatsBack;

    // HISTORY
    @FXML
    private VBox historyList;
    @FXML
    private Label totalGamesLabel;
    @FXML
    private Label bestScoreLabel;
    @FXML
    private Label avgScoreLabel;
    @FXML
    private Button btnHistBack;
    @FXML
    private Button btnExport;

    // SETTINGS / CONFIG
    @FXML
    private TextField pseudoField;
    @FXML
    private TextField apiKeyField;
    @FXML
    private Label lblSettingsTitle;
    @FXML
    private RadioButton themeLight, themeDark;
    @FXML
    private RadioButton langFr, langEn;
    @FXML
    private ToggleGroup themeGroup;
    @FXML
    private ToggleGroup langGroup;
    @FXML
    private Button btnSettingsBack;
    @FXML
    private Button btnSave;

    // --- LOGIC VARIABLES ---
    private ResourceBundle bundle;
    private DeepSeekService deepSeekService;
    private String currentLetter;
    private int score = 0;
    private int timeLeft = 120; // seconds
    private Timeline timeline;
    private Map<String, TextField> gameFields = new HashMap<>();
    private Map<String, Integer> currentWordScores = new HashMap<>(); // Track points per field
    private String currentLanguage = "fr"; // UI Language
    private String gameLanguage = "fr"; // Word/Game Language
    private Player currentPlayer;
    private boolean isTimerEnabled = true;

    // Categories Cache
    private ObservableList<Category> allCategoriesEntry = FXCollections.observableArrayList();

    // Multiplayer
    private GameServer gameServer;
    private GameClient gameClient;
    private boolean isMultiplayer = false;
    private boolean isHost = false;
    private Map<String, Integer> multiplayerScores = new HashMap<>();

    // --- INITIALIZATION ---
    private String currentTheme = "light";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[INITIALIZE] Starting MainController...");
        this.bundle = resources;
        this.deepSeekService = new DeepSeekService();

        // Init DB
        initDatabase();

        // Listeners for Mode
        modeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMulti = (newVal == radioMulti);
            multiplayerConfigBox.setVisible(isMulti);
            multiplayerConfigBox.setManaged(isMulti);
        });

        // Setup Categories List factory
        categoriesList.setCellFactory(param -> new ListCell<HBox>() {
            @Override
            protected void updateItem(HBox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(item);
                    setText(null);
                }
            }
        });

        loadCurrentPlayer();
        updatePlayerPointsUI();

        // Theme delayed application (wait for scene)
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyTheme(currentTheme);
            }
        });

        // Apply initial texts
        Platform.runLater(this::reloadAllTexts);

        // Sync timer setting if host
        cbEnableTimer.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (isMultiplayer && isHost && gameServer != null) {
                broadcastSettings();
            }
        });
    }

    private void loadCurrentPlayer() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Simple logic: Load first player or create "Player1"
            List<Player> players = session.createQuery("FROM Player", Player.class).list();
            if (players.isEmpty()) {
                Transaction tx = session.beginTransaction();
                Player p = new Player("Player1");
                p.setPoints(50); // Welcome bonus
                session.save(p);
                tx.commit();
                this.currentPlayer = p;
            } else {
                this.currentPlayer = players.get(0);
            }
        }
    }

    private void updatePlayerPointsUI() {
        if (currentPlayer != null) {
            String pts = String.valueOf(currentPlayer.getPoints());
            if (pointsLabel != null)
                pointsLabel.setText(pts);
            if (playerPointsLabel != null)
                playerPointsLabel.setText(pts);
        }
    }

    private void initDatabase() {
        // Simple check to ensure mandatory categories exist
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Long count = (Long) session.createQuery("SELECT count(c) FROM Category c").uniqueResult();
            if (count == 0) {
                session.save(new Category("Pays", true, true));
                session.save(new Category("Ville", true, true));
                session.save(new Category("Animal", true, false));
                session.save(new Category("Métier", true, false));
                Category marque = new Category("Marque", true, false);
                marque.setActive(false);
                session.save(marque);
            }
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- NAVIGATION ---
    private void showPane(Region pane) {
        homePane.setVisible(false);
        setupPane.setVisible(false);
        gamePane.setVisible(false);
        resultsPane.setVisible(false);
        categoriesPane.setVisible(false);
        historyPane.setVisible(false);
        settingsPane.setVisible(false);

        pane.setVisible(true);
    }

    @FXML
    private void showHome() {
        showPane(homePane);
    }

    @FXML
    private void showSetup() {
        loadCategories();
        updateCategoriesSelectionUI();
        showPane(setupPane);
    }

    @FXML
    private void showCategories() {
        loadCategories();
        refreshCategoriesManagerUI();
        showPane(categoriesPane);
    }

    @FXML
    private void showHistory() {
        refreshHistoryUI();
        showPane(historyPane);
    }

    private void refreshHistoryUI() {
        System.out.println("[DEBUG] Tentative de chargement de l'historique...");
        historyList.getChildren().clear();

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Game> games = session.createQuery("FROM Game ORDER BY date DESC", Game.class).list();
            System.out.println("[DEBUG] " + games.size() + " parties récupérées.");

            // Calculate statistics
            int totalGames = games.size();
            int bestScore = games.isEmpty() ? 0 : games.stream().mapToInt(Game::getScore).max().orElse(0);
            double avgScore = games.isEmpty() ? 0 : games.stream().mapToInt(Game::getScore).average().orElse(0);

            // Update UI components on UI Thread
            Platform.runLater(() -> {
                try {
                    totalGamesLabel.setText(String.valueOf(totalGames));
                    bestScoreLabel.setText(String.valueOf(bestScore));
                    avgScoreLabel.setText(String.format(Locale.US, "%.1f", avgScore));

                    if (games.isEmpty()) {
                        Label emptyLabel = new Label("Aucune partie enregistrée.");
                        emptyLabel.setPadding(new Insets(20));
                        emptyLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 14px;");
                        historyList.getChildren().add(emptyLabel);
                    } else {
                        // Display recent games (max 10)
                        List<Game> recentGames = games.stream().limit(10).collect(Collectors.toList());
                        for (Game g : recentGames) {
                            historyList.getChildren().add(createHistoryCard(g));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erreur UI Historique: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("Erreur Hibernate Historique: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> {
                historyList.getChildren().add(new Label("Erreur de chargement: " + e.getMessage()));
            });
        }
    }

    private VBox createHistoryCard(Game g) {
        VBox card = new VBox(10);
        card.getStyleClass().add("modern-card");
        card.setPadding(new Insets(15));

        // Header row with date and letter
        HBox header = new HBox(15);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        String dateStr = g.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Label dateLbl = new Label("📅 " + dateStr);
        dateLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");

        Label letterLbl = new Label("Lettre: " + g.getLetter());
        letterLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #6c63ff;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label scoreLbl = new Label(g.getScore() + " pts");
        scoreLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #4CAF50;");

        header.getChildren().addAll(dateLbl, letterLbl, spacer, scoreLbl);
        card.getChildren().add(header);

        // Mode and Rank row
        HBox details = new HBox(10);
        details.setAlignment(Pos.CENTER_LEFT);

        String mode = g.getMode() != null ? g.getMode() : "SOLO";
        Label modeLbl = new Label(mode.equals("MULTI") ? "👥 MULTI" : "👤 SOLO");
        modeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4; "
                + (mode.equals("MULTI") ? "-fx-background-color: #E3F2FD; -fx-text-fill: #1976D2;"
                        : "-fx-background-color: #F5F5F5; -fx-text-fill: #616161;"));

        details.getChildren().add(modeLbl);

        if ("MULTI".equals(mode) && g.getPlayerRank() > 0) {
            Label rankLbl = new Label("Rang: #" + g.getPlayerRank() + "/" + g.getTotalPlayers());
            rankLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            details.getChildren().add(rankLbl);
        }

        card.getChildren().add(details);
        return card;
    }

    @FXML
    private void showSettings() {
        showPane(settingsPane);
    }

    // --- CATEGORIES MANAGEMENT LOGIC ---

    private void loadCategories() {
        allCategoriesEntry.clear();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Category> cats = session.createQuery("FROM Category", Category.class).list();
            allCategoriesEntry.addAll(cats);
        }
    }

    private void refreshCategoriesManagerUI() {
        // 1. Counters
        long activeCount = allCategoriesEntry.stream().filter(Category::isActive).count();
        activeCountLabel.setText("Active: " + activeCount + "/12");
        activeProgressBar.setProgress(activeCount / 12.0);

        // 2. Preview
        refreshGamePreview(activeCount);

        // 3. List
        categoriesList.getItems().clear();

        // Split System vs Custom
        List<Category> systemCats = allCategoriesEntry.stream().filter(Category::isSystem).collect(Collectors.toList());
        List<Category> customCats = allCategoriesEntry.stream().filter(c -> !c.isSystem()).collect(Collectors.toList());

        // Add Header System
        categoriesList.getItems().add(createSectionHeader("CATÉGORIES SYSTÈME"));
        for (Category c : systemCats) {
            categoriesList.getItems().add(createCategoryRow(c));
        }

        // Add Header Custom
        categoriesList.getItems().add(createSectionHeader("CATÉGORIES PERSONNALISÉES"));
        for (Category c : customCats) {
            categoriesList.getItems().add(createCategoryRow(c));
        }
    }

    private HBox createSectionHeader(String title) {
        HBox hbox = new HBox();
        hbox.setPadding(new Insets(10, 0, 5, 0));
        hbox.setStyle("-fx-border-color: transparent transparent #ddd transparent;");
        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #999; -fx-font-size: 12px;");
        hbox.getChildren().add(lbl);
        return hbox;
    }

    private HBox createCategoryRow(Category c) {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setStyle("-fx-background-color: " + (c.isSystem() ? "#F9F9F9" : "WHITE") + "; -fx-background-radius: 5px;");

        // CheckBox (Status for toggle) - except for Mandatory
        CheckBox cb = new CheckBox();
        cb.setSelected(c.isActive());
        if (c.isMandatory()) {
            cb.setDisable(true);
        } else {
            cb.setOnAction(e -> toggleCategoryActivation(c, cb));
        }

        // Name & Icon
        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.setPrefWidth(200);
        Label icon = new Label(c.isSystem() ? "🏢" : "🚀"); // Simple icons
        Label name = new Label(c.getName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        nameBox.getChildren().addAll(icon, name);

        // Status Badge
        Label statusBadge = new Label(c.isActive() ? "✅ Actif" : "❌ Inactif");
        statusBadge.setPrefWidth(100);
        statusBadge.setStyle("-fx-text-fill: " + (c.isActive() ? "#4CAF50" : "#F44336") + ";");

        // Type Badge
        Label typeBadge = new Label(c.isSystem() ? "★ Défaut" : "✨ Perso");
        typeBadge.setPrefWidth(120);
        typeBadge.setStyle(
                "-fx-text-fill: #666; -fx-background-color: #eee; -fx-padding: 2 6; -fx-background-radius: 4;");

        // Actions
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(actions, Priority.ALWAYS);

        if (c.isSystem()) {
            Label lock = new Label("🔒");
            actions.getChildren().add(lock);
        } else {
            Button editBtn = new Button("✏️");
            editBtn.setStyle("-fx-background-color: transparent;");
            editBtn.setOnAction(e -> editCategory(c));

            Button delBtn = new Button("🗑️");
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red;");
            delBtn.setOnAction(e -> deleteCategory(c));

            actions.getChildren().addAll(editBtn, delBtn);
        }

        row.getChildren().addAll(cb, nameBox, statusBadge, typeBadge, actions);
        return row;
    }

    private void toggleCategoryActivation(Category c, CheckBox cb) {
        long currentActive = allCategoriesEntry.stream().filter(Category::isActive).count();
        boolean isActivating = cb.isSelected();

        if (isActivating) {
            if (currentActive >= 12) {
                cb.setSelected(false);
                showAlert("Limite atteinte", "Maximum 12 catégories actives.");
                return;
            }
        } else {
            if (currentActive <= 3) {
                cb.setSelected(true);
                showAlert("Minimum requis", "Vous devez garder au moins 3 catégories actives.");
                return;
            }
        }

        updateCategory(c, isActivating);
        refreshCategoriesManagerUI(); // Refresh preview and counters

        if (isMultiplayer && isHost && gameServer != null) {
            broadcastSettings();
        }
    }

    private void updateCategory(Category c, boolean active) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            c.setActive(active);
            session.update(c); // Use merge if detached, but here we reload or use same obj
            tx.commit();
        }
        loadCategories(); // Reload to refresh UI properly
        refreshCategoriesManagerUI();
    }

    @FXML
    private void addCategory() {
        String name = newCatNameField.getText().trim();
        if (name.length() < 2 || name.length() > 50) {
            showToast("Le nom doit faire entre 2 et 50 caractères.");
            return;
        }

        // Check duplicate
        if (allCategoriesEntry.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name))) {
            showToast("Cette catégorie existe déjà.");
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Category newCat = new Category();
            newCat.setName(name);
            newCat.setSystem(false);
            newCat.setActive(true); // Active by default
            newCat.setMandatory(false);
            session.save(newCat);
            tx.commit();
        }

        showToast("Catégorie ajoutée !");
        newCatNameField.clear();
        loadCategories();
        refreshCategoriesManagerUI();
    }

    private void deleteCategory(Category c) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer la catégorie '" + c.getName() + "' ?",
                ButtonType.YES, ButtonType.NO);
        alert.showAndWait();
        if (alert.getResult() == ButtonType.YES) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                session.delete(c);
                tx.commit();
            }
            loadCategories();
            refreshCategoriesManagerUI();
        }
    }

    private void editCategory(Category c) {
        TextInputDialog dialog = new TextInputDialog(c.getName());
        dialog.setTitle("Renommer");
        dialog.setHeaderText("Nouveau nom pour " + c.getName());
        dialog.showAndWait().ifPresent(newName -> {
            if (newName.length() >= 2) {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    Transaction tx = session.beginTransaction();
                    c.setName(newName);
                    session.update(c);
                    tx.commit();
                }
                loadCategories();
                refreshCategoriesManagerUI();
            }
        });
    }

    private void refreshGamePreview(long activeCount) {
        previewCategoriesBox.getChildren().clear();
        // Just show first 4 active categories
        allCategoriesEntry.stream()
                .filter(Category::isActive)
                .limit(4)
                .forEach(c -> {
                    Label lbl = new Label(c.getName().toUpperCase());
                    lbl.setStyle(
                            "-fx-background-color: #E0E0E0; -fx-padding: 5 10; -fx-background-radius: 15; -fx-font-weight: bold;");
                    previewCategoriesBox.getChildren().add(lbl);
                });

        if (activeCount < 3) {
            Label warn = new Label("⚠️ Min 3 cat.");
            warn.setStyle("-fx-text-fill: red;");
            previewCategoriesBox.getChildren().add(warn);
        }
    }

    // --- GAME LOGIC (UPDATED WITH DB CATEGORIES) ---

    @FXML
    private void startGame() {
        // Reload fresh from DB
        loadCategories();
        List<Category> activeCats = allCategoriesEntry.stream().filter(Category::isActive).collect(Collectors.toList());

        if (activeCats.size() < 3) {
            showAlert("Erreur", "Moins de 3 catégories actives ! Configurez les catégories.");
            return;
        }

        System.out.println("[DEBUG] startGame() called. isMultiplayer=" + isMultiplayer + ", isHost=" + isHost
                + ", gameServer=" + (gameServer != null));
        if (isMultiplayer && isHost && gameServer != null) {
            // Multiplayer Host logic: Pick letter and categories, then tell server to start
            char c = (char) ('A' + new Random().nextInt(26));
            String letter = String.valueOf(c);
            String lang = "fr";
            List<String> catNames = activeCats.stream()
                    .map(Category::getName)
                    .collect(Collectors.toList());

            gameServer.startGame(letter, lang, catNames, cbEnableTimer.isSelected());
            System.out.println(
                    "[HOST] Sent startGame signal with letter " + letter + " and " + catNames.size() + " categories.");
            // We don't setup UI here, we wait for the server broadcast to our local client
            return;
        }

        // Single Player logic
        char c = (char) ('A' + new Random().nextInt(26));
        currentLetter = String.valueOf(c);
        gameLetterLabel.setText(currentLetter);
        this.gameLanguage = "fr";

        // Reset
        score = 0;
        currentWordScores.clear();
        updateScoreUI();
        timeLeft = 120;
        timerLabel.setText(formatTime(timeLeft));
        progressBar.setProgress(0);

        // Build Grid
        activeCats.sort((c1, c2) -> {
            if (c1.isSystem() != c2.isSystem())
                return c1.isSystem() ? -1 : 1;
            return c1.getName().compareTo(c2.getName());
        });
        buildGameGrid(activeCats);

        // Timer
        this.isTimerEnabled = cbEnableTimer.isSelected();
        timerLabel.setVisible(isTimerEnabled);
        progressBar.setVisible(isTimerEnabled);
        btnHint.setVisible(true);

        if (timeline != null)
            timeline.stop();
        if (isTimerEnabled) {
            timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                timeLeft--;
                timerLabel.setText(formatTime(timeLeft));
                progressBar.setProgress(1.0 - (timeLeft / 120.0));
                if (timeLeft <= 0)
                    finishGame();
            }));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
        }

        showPane(gamePane);
    }

    private void buildGameGrid(List<Category> categories) {
        gameGrid.getChildren().clear();
        gameFields.clear();
        gameGrid.getRowConstraints().clear();
        gameGrid.getColumnConstraints().clear();

        // Ensure 2 columns with gaps
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        gameGrid.getColumnConstraints().addAll(col1, col2);
        gameGrid.setHgap(20);
        gameGrid.setVgap(15);

        int row = 0;
        int col = 0;
        int index = 0;

        for (Category catObj : categories) {
            String cat = catObj.getName();

            // Create container VBox for Label + Input (stacked like mockup)
            VBox box = new VBox(5);

            Label lbl = new Label(cat.toUpperCase());
            lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #333; -fx-font-size: 14px;");

            TextField tf = new TextField();
            tf.setPromptText("Votre réponse...");
            tf.getStyleClass().add("game-field");
            tf.setStyle("-fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #ccc; -fx-border-radius: 8;");

            setupValidation(tf, cat);

            box.getChildren().addAll(lbl, tf);

            // Calc grid pos: 0,0 | 1,0 | 0,1 | 1,1 ... NO -> 2 columns means Left Col (0)
            // then Right Col (1) ?
            // Mockup says: Col Left: 0,1,2,3... Col Right: 0,1,2,3...
            // Simple logic: index % 2 determines column. index / 2 determines row.

            int c = index % 2;
            int r = index / 2;

            gameGrid.add(box, c, r);
            gameFields.put(cat, tf);

            index++;
        }
    }

    // --- VALIDATION & UTILS (Keep existing) ---
    private void setupValidation(TextField tf, String category) {
        // Only trigger API validation on loose focus OR Enter key, not every keystroke
        tf.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER:
                    gameFields.values().stream()
                            .filter(f -> f != tf && f.getText().isEmpty()) // find next empty
                            .findFirst()
                            .ifPresent(TextField::requestFocus);
                    // Also validate logic
                    checkAndValidate(tf, category);
                    break;
                default:
                    break;
            }
        });

        tf.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                checkAndValidate(tf, category);
            } else {
                // On focus gain, maybe clear invalid style if user wants to retry?
                // tf.setStyle("-fx-border-color: #2196F3;"); // Blue focus
            }
        });
    }

    private void checkAndValidate(TextField tf, String category) {
        String text = tf.getText();
        if (text == null || text.trim().isEmpty()) {
            resetFieldStyle(tf);
            return;
        }

        String clean = text.trim();

        // 1. Immediate invalid check (Wrong letter)
        if (currentLetter != null && !clean.toUpperCase().startsWith(currentLetter)) {
            setFieldInvalid(tf);
            return;
        }

        // 2. Pending State (Grey/Loading)
        tf.setStyle("-fx-border-color: #FFC107; -fx-background-color: #FFF8E1;");

        if (isMultiplayer && gameClient != null && gameClient.isConnected()) {
            gameClient.submitWord(category, clean);
        } else {
            validateWithApi(tf, category, clean);
        }
    }

    private void validateWithApi(TextField tf, String category, String word) {
        System.out.println(
                "[DEBUG] validateWithApi - word: " + word + ", category: " + category + ", language: " + gameLanguage);
        new Thread(() -> {
            ValidationResult result = deepSeekService.validateWord(category, word, currentLetter, gameLanguage);
            Platform.runLater(() -> {
                if (result.isValid) {
                    setFieldValid(tf);
                    currentWordScores.put(category, result.score);
                } else {
                    setFieldInvalid(tf);
                    currentWordScores.put(category, 0);
                }
                recalculateTotalScore();
            });
        }).start();
    }

    private void recalculateTotalScore() {
        // Base score from words
        int base = currentWordScores.values().stream().mapToInt(Integer::intValue).sum();

        // Completion Bonus (+10 if all valid)
        boolean allValid = gameFields.size() == currentWordScores.size() &&
                currentWordScores.values().stream().allMatch(s -> s > 0);
        int completionBonus = allValid ? 10 : 0;

        // Time Bonus (+1 pt per 20s remaining) - Only if enabled
        int timeBonus = isTimerEnabled ? (timeLeft / 20) : 0;

        this.score = base + completionBonus + timeBonus;
        scoreLabel.setText(bundle.getString("score.total") + ": " + this.score);
    }

    private void setFieldValid(TextField tf) {
        tf.getStyleClass().removeAll("field-invalid", "field-neutral");
        if (!tf.getStyleClass().contains("field-valid"))
            tf.getStyleClass().add("field-valid");
        tf.setStyle("-fx-border-color: #4CAF50; -fx-background-color: #E8F5E9;");
    }

    private void setFieldInvalid(TextField tf) {
        tf.getStyleClass().removeAll("field-valid", "field-neutral");
        if (!tf.getStyleClass().contains("field-invalid"))
            tf.getStyleClass().add("field-invalid");
        tf.setStyle("-fx-border-color: #F44336; -fx-background-color: #FFEBEE;");
    }

    private void resetFieldStyle(TextField tf) {
        tf.getStyleClass().removeAll("field-valid", "field-invalid");
        tf.setStyle("");
    }

    @FXML
    private void finishGame() {
        if (timeline != null)
            timeline.stop();

        if (isMultiplayer) {
            if (isHost && gameServer != null) {
                // If we are host, we tell the server to end for everyone
                gameServer.endGame();
            }
            // If client, we just wait for the GAME_END broadcast which will trigger
            // showMultiplayerResults
            return;
        }

        // Single Player logic below
        recalculateTotalScore(); // Final sync

        finalLetterLabel.setText("Lettre de la partie : " + currentLetter);
        finalScoreLabel.setText(score + " pts");
        resultsListBox.getChildren().clear();

        gameFields.forEach((cat, tf) -> {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5));
            row.getStyleClass().add("card");

            int wordScore = currentWordScores.getOrDefault(cat, 0);
            boolean isValid = wordScore > 0;

            Label icon = new Label(isValid ? "✅" : "❌");
            Label catLbl = new Label(cat.toUpperCase());
            catLbl.setPrefWidth(120);
            catLbl.setStyle("-fx-font-weight: bold;");

            Label wordLbl = new Label(tf.getText().isEmpty() ? "-" : tf.getText());
            wordLbl.setPrefWidth(150);

            Label ptsLbl = new Label("+" + wordScore);
            ptsLbl.setStyle(isValid ? "-fx-text-fill: green;" : "-fx-text-fill: red;");

            row.getChildren().addAll(icon, catLbl, wordLbl, ptsLbl);
            resultsListBox.getChildren().add(row);
        });

        showPane(resultsPane);
        saveGameResult();
    }

    private void saveGameResult() {
        saveGameResult(1, 1);
    }

    private void saveGameResult(int playerRank, int totalPlayers) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            // 1. Save Game
            Game game = new Game();
            game.setDate(LocalDateTime.now());
            game.setDurationSeconds(120 - timeLeft);
            game.setLetter(currentLetter);
            game.setScore(score);
            game.setMode(isMultiplayer ? "MULTI" : "SOLO");
            game.setPlayerRank(playerRank);
            game.setTotalPlayers(totalPlayers);
            session.save(game);

            // 2. Update Player Points
            if (currentPlayer != null) {
                int earned = score;
                currentPlayer.setPoints(currentPlayer.getPoints() + earned);
                session.update(currentPlayer);

                Platform.runLater(() -> {
                    updatePlayerPointsUI();
                    showToast("Points gagnés: " + earned);
                });
            }

            tx.commit();
            System.out.println("Partie sauvegardée : " + game.getMode() + " - Score: " + score);
        } catch (Exception e) {
            System.err.println("Erreur sauvegarde : " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void quitGame() {
        if (timeline != null)
            timeline.stop();
        showHome();
    }

    @FXML
    private void useHint() {
        // Same hint logic
        ContextMenu menu = new ContextMenu();
        MenuItem itemBonus = new MenuItem("Temps Bonus (+30s) (3 pts)");
        itemBonus.setOnAction(e -> applyBonusTime());
        MenuItem itemReveal = new MenuItem("Révélation (5 pts)");
        itemReveal.setOnAction(e -> applyRevealWord());
        menu.getItems().addAll(itemBonus, itemReveal);
        javafx.geometry.Point2D p = btnHint.localToScreen(0, 0);
        menu.show(btnHint, p.getX(), p.getY() - menu.getHeight());
    }

    private void applyBonusTime() {
        if (currentPlayer == null)
            return;

        int cost = 3;
        if (currentPlayer.getPoints() >= cost) {
            updatePlayerPoints(-cost);
            timeLeft += 30;
            timerLabel.setText(formatTime(timeLeft));
            showToast("Bonus temps activé ! (-" + cost + " pts joueur)");
        } else {
            showToast("Points joueur insuffisants (" + cost + " requis) !");
        }
    }

    private void updatePlayerPoints(int delta) {
        if (currentPlayer == null)
            return;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            currentPlayer.setPoints(currentPlayer.getPoints() + delta);
            session.update(currentPlayer);
            tx.commit();
        }
        updatePlayerPointsUI();
    }

    private void applyRevealWord() {
        if (currentPlayer == null)
            return;

        int cost = 3; // UPDATED COST as requested
        if (currentPlayer.getPoints() >= cost) {
            Optional<Map.Entry<String, TextField>> emptyEntry = gameFields.entrySet().stream()
                    .filter(e -> e.getValue().getText().isEmpty()
                            || !e.getValue().getStyleClass().contains("field-valid"))
                    .findFirst();

            if (emptyEntry.isPresent()) {
                String cat = emptyEntry.get().getKey();
                TextField tf = emptyEntry.get().getValue();

                showToast("Recherche d'un mot... (-" + cost + " pts)");
                updatePlayerPoints(-cost);

                System.out.println("[DEBUG] applyRevealWord - category: " + cat + ", language: " + gameLanguage);
                new Thread(() -> {
                    String suggestion = deepSeekService.suggestWord(cat, currentLetter, gameLanguage);
                    Platform.runLater(() -> {
                        if (suggestion != null && !suggestion.equalsIgnoreCase("Erreur")) {
                            tf.setText(suggestion);
                            validateWithApi(tf, cat, suggestion);
                            showToast("Suggestion: " + suggestion);
                        } else {
                            showToast("Impossible de trouver un mot !");
                            updatePlayerPoints(cost); // Refund
                        }
                    });
                }).start();
            } else {
                showToast("Toutes les cases sont déjà remplies !");
            }
        } else {
            showToast("Besoin de " + cost + " points !");
        }
    }

    private void showToast(String msg) {
        Label toast = new Label(msg);
        toast.setStyle(
                "-fx-background-color: #333; -fx-text-fill: white; -fx-padding: 10px; -fx-background-radius: 5px;");
        rootPane.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 100, 0));
        new Timeline(new KeyFrame(Duration.seconds(2), e -> rootPane.getChildren().remove(toast))).play();
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateScoreUI() {
        scoreLabel.setText("Score: " + score);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updateLocale(Locale locale) {
        // Simplified I18N for now, keeps existing logic
        this.bundle = ResourceBundle.getBundle("i18n/messages", locale);
        // updateTexts() implementation would be here to refresh UI strings
    }

    private void updateCategoriesSelectionUI() {
        // Only used if Setup screen needs a simplified view, but assume we use the main
        // manager now
        categoriesSelectionBox.getChildren().clear();
        allCategoriesEntry.stream().filter(Category::isActive).forEach(c -> {
            Label lbl = new Label("• " + c.getName());
            categoriesSelectionBox.getChildren().add(lbl);
        });
    }

    private void broadcastSettings() {
        if (gameServer != null && isHost) {
            List<String> activeCats = allCategoriesEntry.stream()
                    .filter(Category::isActive)
                    .map(Category::getName)
                    .collect(Collectors.toList());
            gameServer.updateSettings(cbEnableTimer.isSelected(), activeCats);
        }
    }

    @FXML
    private void hostGame() {
        if (gameServer != null) {
            showToast("Serveur déjà démarré");
            return;
        }

        // Reset state
        isHost = false;
        isMultiplayer = false;
        if (gameClient != null)
            gameClient.disconnect();

        // Create server with callbacks
        gameServer = new GameServer(new GameServer.ServerCallback() {
            @Override
            public void onPlayerJoined(String playerName) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Joueur connecté: " + playerName);
                    connectionStatusLabel.setStyle("-fx-text-fill: green;");
                    showToast(playerName + " a rejoint la partie");
                });
            }

            @Override
            public void onPlayerLeft(String playerName) {
                Platform.runLater(() -> showToast(playerName + " a quitté la partie"));
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Erreur: " + error);
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                });
            }

            @Override
            public void onServerStarted() {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Serveur démarré sur port 8888");
                    connectionStatusLabel.setStyle("-fx-text-fill: green;");
                    isHost = true;
                    isMultiplayer = true;

                    // Automatically join as a client to unify logic
                    serverIpField.setText("127.0.0.1");
                    joinGame();

                    // Capture and broadcast current host settings
                    loadCategories();
                    broadcastSettings();
                });
            }
        });

        gameServer.start();
    }

    @FXML
    private void joinGame() {
        if (gameClient != null && gameClient.isConnected()) {
            showToast("Déjà connecté au serveur");
            return;
        }

        // IMPORTANT: Only reset isHost if we are NOT actually hosting locally
        boolean isLocalHost = isHost && gameServer != null;
        if (!isLocalHost) {
            isHost = false;
        }
        isMultiplayer = true;

        // Disable start button for joiners
        btnStart.setDisable(!isHost);

        String serverIp = serverIpField.getText().trim();
        if (serverIp.isEmpty()) {
            showToast("Veuillez entrer l'IP du serveur");
            return;
        }

        String playerName = currentPlayer != null ? currentPlayer.getUsername() : "Player";

        // Create client with callbacks
        gameClient = new GameClient(playerName, new GameClient.ClientCallback() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Connecté au serveur");
                    connectionStatusLabel.setStyle("-fx-text-fill: green;");
                    isMultiplayer = true;
                    // Important: only set isHost to false if it's not already true
                    if (!isHost) {
                        isHost = false;
                        btnStart.setDisable(true);
                    }
                });
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Déconnecté: " + reason);
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                    showToast("Déconnecté: " + reason);
                });
            }

            @Override
            public void onPlayerListUpdate(List<String> players) {
                Platform.runLater(() -> showToast(players.size() + " joueur(s) connecté(s)"));
            }

            @Override
            public void onSettingsUpdate(boolean timerEnabled, List<String> categories) {
                Platform.runLater(() -> {
                    if (!isHost) {
                        cbEnableTimer.setDisable(true);
                        cbEnableTimer.setSelected(timerEnabled);

                        // Update categories display
                        categoriesSelectionBox.getChildren().clear();
                        if (categories != null) {
                            if (!categories.isEmpty()) {
                                Label syncLabel = new Label("✓ Synchronisé");
                                syncLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-font-size: 10px;");
                                categoriesSelectionBox.getChildren().add(syncLabel);
                            }
                            for (String catName : categories) {
                                Label lbl = new Label("• " + catName);
                                categoriesSelectionBox.getChildren().add(lbl);
                            }
                        }
                    }
                });
            }

            @Override
            public void onGameStart(String letter, String language, List<String> categories, boolean timerEnabled) {
                System.out.println(
                        "[CLIENT] CRITICAL: Signal GAME_START reçu. Letter=" + letter + ", Timer=" + timerEnabled);
                Platform.runLater(() -> {
                    try {
                        currentLetter = letter;
                        gameLanguage = language;
                        gameLetterLabel.setText(letter);

                        isTimerEnabled = timerEnabled;

                        // Sync settings UI for non-host
                        if (!isHost) {
                            cbEnableTimer.setDisable(true);
                            cbEnableTimer.setSelected(timerEnabled);
                            categoriesSelectionBox.setDisable(true);
                            btnStart.setDisable(true);
                        }

                        // Build grid with categories from server
                        List<Category> cats = new ArrayList<>();
                        for (String catName : categories) {
                            Category cat = new Category();
                            cat.setName(catName);
                            cats.add(cat);
                        }
                        buildGameGrid(cats);

                        // Start timer if enabled
                        if (isTimerEnabled) {
                            timeLeft = 120;
                            if (timeline != null)
                                timeline.stop();
                            timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                                timeLeft--;
                                timerLabel.setText(formatTime(timeLeft));
                                progressBar.setProgress(1.0 - (timeLeft / 120.0));
                                if (timeLeft <= 0)
                                    finishGame();
                            }));
                            timeline.setCycleCount(Timeline.INDEFINITE);
                            timeline.play();
                        }

                        showPane(gamePane);
                        rootPane.requestLayout(); // Force re-render
                        System.out.println("[CLIENT] Game Pane is now ACTIVE.");
                        showToast("Partie démarrée avec la lettre " + letter);
                    } catch (Exception e) {
                        System.err.println("[CLIENT] Error in onGameStart: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onWordValidated(String player, String category, String word, boolean valid, int score) {
                Platform.runLater(() -> {
                    // Update UI if it's our word
                    if (player.equals(playerName)) {
                        TextField tf = gameFields.get(category);
                        if (tf != null) {
                            if (valid) {
                                setFieldValid(tf);
                                currentWordScores.put(category, score);
                            } else {
                                setFieldInvalid(tf);
                                currentWordScores.put(category, 0);
                            }
                        }
                    }
                    showToast(player + ": " + word + " (" + (valid ? "✓" : "✗") + ")");
                });
            }

            @Override
            public void onScoreUpdate(Map<String, Integer> scores) {
                Platform.runLater(() -> {
                    multiplayerScores.clear();
                    multiplayerScores.putAll(scores);

                    // Update our score display
                    Integer ourScore = scores.get(playerName);
                    if (ourScore != null) {
                        score = ourScore;
                        updateScoreUI();
                    }
                });
            }

            @Override
            public void onGameEnd(Map<String, Integer> rankings, Map<String, Map<String, String>> playerWords,
                    Map<String, Map<String, ValidationResult>> playerValidations) {
                Platform.runLater(() -> {
                    if (timeline != null)
                        timeline.stop();
                    showMultiplayerResults(rankings, playerWords, playerValidations);
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Erreur: " + error);
                    connectionStatusLabel.setStyle("-fx-text-fill: red;");
                    showToast("Erreur: " + error);
                });
            }
        });

        // Connect to server
        gameClient.connect(serverIp, 8888);
        connectionStatusLabel.setText("Connexion en cours...");
        connectionStatusLabel.setStyle("-fx-text-fill: orange;");
    }

    @FXML
    private void exportCSV() {
    }

    @FXML
    private void testApi() {
        String key = apiKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showAlert("Erreur", "Veuillez entrer une clé API.");
            return;
        }

        DeepSeekService.setApiKey(key);
        new Thread(() -> {
            String result = deepSeekService.suggestWord("fruit", "A", "fr");
            Platform.runLater(() -> {
                if (result != null && !result.equals("Erreur")) {
                    showAlert("Succès", "API fonctionnelle ! Suggestion reçue: " + result);
                } else {
                    showAlert("Erreur", "Échec du test API. Vérifiez votre clé.");
                }
            });
        }).start();
    }

    @FXML
    private void saveSettings() {
        // Apply API Key
        String key = apiKeyField.getText();
        if (key != null && !key.trim().isEmpty()) {
            DeepSeekService.setApiKey(key);
        }

        // Apply Language (Immediate)
        if (langFr.isSelected()) {
            this.currentLanguage = "fr";
            updateLocale(Locale.FRENCH);
        } else if (langEn.isSelected()) {
            this.currentLanguage = "en";
            updateLocale(Locale.ENGLISH);
        }

        // Apply Theme (Immediate)
        if (themeDark.isSelected()) {
            switchTheme("dark");
        } else {
            switchTheme("light");
        }

        showHome();
        reloadAllTexts(); // Refresh UI texts immediately
    }

    @FXML
    public void switchTheme(String theme) {
        this.currentTheme = theme;
        applyTheme(theme);
    }

    private void applyTheme(String theme) {
        if (rootPane.getScene() == null)
            return;
        Scene scene = rootPane.getScene();

        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        if ("dark".equals(theme)) {
            scene.getStylesheets().add(getClass().getResource("/dark.css").toExternalForm());
            rootPane.getStyleClass().add("dark-mode");
        } else {
            scene.getStylesheets().add(getClass().getResource("/light.css").toExternalForm());
            rootPane.getStyleClass().remove("dark-mode");
        }
    }

    private void reloadAllTexts() {
        if (bundle == null)
            return;

        // Home
        btnPlay.setText(bundle.getString("btn.play"));
        btnCats.setText(bundle.getString("btn.categories"));
        btnScores.setText(bundle.getString("btn.scores"));
        btnConfig.setText(bundle.getString("btn.config"));

        lblAppTitle.setText(bundle.getString("app.title"));
        lblAppSubtitle.setText(bundle.getString("app.subtitle"));

        // Setup
        lblSetupTitle.setText(bundle.getString("setup.title"));
        lblGameMode.setText(bundle.getString("setup.mode"));
        lblSelectCats.setText(bundle.getString("setup.select_categories"));
        lblMinCats.setText(currentLanguage.equals("fr") ? "(Minimum 3 sélectionnées)" : "(Minimum 3 selected)");
        btnBackSetup.setText(bundle.getString("btn.back"));
        btnStart.setText(bundle.getString("btn.start"));

        // Settings
        lblSettingsTitle.setText(bundle.getString("settings.title"));
        btnSettingsBack.setText(bundle.getString("btn.back"));
        btnSave.setText(bundle.getString("btn.save"));

        // Game
        btnEnd.setText(bundle.getString("btn.end"));
        btnHint.setText("💡 " + (currentLanguage.equals("en") ? "HINT" : "AIDE"));

        // Results
    }

    // --- MULTIPLAYER HELPER METHODS ---

    private void showMultiplayerResults(Map<String, Integer> rankings,
            Map<String, Map<String, String>> playerWords,
            Map<String, Map<String, ValidationResult>> playerValidations) {

        // --- SORT PLAYERS BY SCORE ---
        List<Map.Entry<String, Integer>> sortedRankings = new ArrayList<>(rankings.entrySet());
        sortedRankings.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        finalLetterLabel.setText("Lettre de la partie : " + currentLetter);
        if (!sortedRankings.isEmpty()) {
            finalScoreLabel.setText(sortedRankings.get(0).getValue() + " pts");
        }

        resultsListBox.getChildren().clear();

        // --- HEADER ROW (Categories) ---
        HBox header = new HBox(10);
        header.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 10; -fx-background-radius: 5;");
        Label catTitle = new Label("CATEGORIE");
        catTitle.setPrefWidth(120);
        catTitle.setStyle("-fx-font-weight: bold;");
        header.getChildren().add(catTitle);

        for (String player : rankings.keySet()) {
            Label pLabel = new Label(player.toUpperCase());
            pLabel.setPrefWidth(150);
            pLabel.setStyle("-fx-font-weight: bold; -fx-alignment: center;");
            header.getChildren().add(pLabel);
        }
        resultsListBox.getChildren().add(header);

        // --- DATA ROWS (One per category) ---
        List<String> categories = playerWords.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .collect(Collectors.toList());

        for (String cat : categories) {
            HBox row = new HBox(10);
            row.setPadding(new Insets(5, 10, 5, 10));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card");

            Label catLbl = new Label(cat.toUpperCase());
            catLbl.setPrefWidth(120);
            catLbl.setStyle("-fx-font-weight: bold;");
            row.getChildren().add(catLbl);

            for (String player : rankings.keySet()) {
                String word = playerWords.getOrDefault(player, new HashMap<>()).getOrDefault(cat, "-");
                ValidationResult res = playerValidations.getOrDefault(player, new HashMap<>()).get(cat);

                VBox cell = new VBox(2);
                cell.setPrefWidth(150);
                cell.setAlignment(Pos.CENTER);

                Label wordLbl = new Label(word);
                Label scoreLbl = new Label(res != null ? (res.isValid ? "✅ +" + res.score : "❌ 0") : "");
                scoreLbl.setStyle(res != null && res.isValid ? "-fx-text-fill: green; -fx-font-size: 10px;"
                        : "-fx-text-fill: red; -fx-font-size: 10px;");

                cell.getChildren().addAll(wordLbl, scoreLbl);
                row.getChildren().add(cell);
            }
            resultsListBox.getChildren().add(row);
        }

        // --- FOOTER RANKING ---
        VBox rankingBox = new VBox(5);
        rankingBox.setPadding(new Insets(20, 0, 0, 0));
        for (int i = 0; i < sortedRankings.size(); i++) {
            Map.Entry<String, Integer> entry = sortedRankings.get(i);
            Label l = new Label((i + 1) + ". " + entry.getKey() + " : " + entry.getValue() + " pts "
                    + (i == 0 ? "🏆" : ""));
            l.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            rankingBox.getChildren().add(l);
        }
        resultsListBox.getChildren().add(rankingBox);

        showPane(resultsPane);

        // --- PERSISTENCE ---
        String ourName = gameClient != null ? gameClient.getPlayerName() : "Player";
        int ourRank = 1;
        for (int i = 0; i < sortedRankings.size(); i++) {
            if (sortedRankings.get(i).getKey().equals(ourName)) {
                ourRank = i + 1;
                break;
            }
        }
        saveGameResult(ourRank, sortedRankings.size());
    }

    private void cleanupMultiplayer() {
        if (gameServer != null) {
            gameServer.stop();
            gameServer = null;
        }
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }
        isMultiplayer = false;
        isHost = false;
        multiplayerScores.clear();
    }

}
