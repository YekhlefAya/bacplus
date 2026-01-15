package com.bacplus.services;

import com.bacplus.models.ValidatedWord;
import com.bacplus.utils.HibernateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.*;
import java.util.stream.Collectors;

public class DeepSeekService {

    private static String apiKey = "sk-0936c649234349f38f085c9122e663c6";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";

    private String getCategoryInLanguage(String category, String language) {
        if ("en".equalsIgnoreCase(language)) {
            String cat = category.toLowerCase().trim();
            if (cat.contains("pays"))
                return "Country";
            if (cat.contains("ville"))
                return "City";
            if (cat.contains("animal"))
                return "Animal";
            if (cat.contains("métier"))
                return "Job/Profession";
            if (cat.contains("marque"))
                return "Brand/Company";
            if (cat.contains("objet"))
                return "Object/Item";
            if (cat.contains("prénom"))
                return "First Name";
            if (cat.contains("célébrité"))
                return "Celebrity";
            return category;
        }
        return category;
    }

    public static void setApiKey(String key) {
        if (key != null && !key.trim().isEmpty()) {
            apiKey = key.trim();
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    // Blacklists for known false positives or invalid inputs
    private static final Set<String> INVALID_WORDS = Set.of(
            "xmabd", "xyzzy", "test", "azerty", "qwerty",
            "aaaa", "bbbb", "cccc", "dddd", "eeee");

    // Fallback dictionary for common words to ensure basic validation if API fails
    private static final Map<String, List<String>> FALLBACK_WORDS = new HashMap<>();
    static {
        FALLBACK_WORDS.put("ville", List.of(
                "alger", "algiers", "amsterdam", "athenes", "berlin", "bruxelles", "bucarest", "casablanca", "dakar",
                "dubai",
                "dublin", "agadir", "ifrane", "oujda", "kenitra", "meknes", "tetouan", "nador", "safi", "el jadida",
                "fes", "geneve", "helsinki", "istanbul", "jerusalem", "lisbonne", "londres", "luxembourg", "madrid",
                "marrakech", "ouarzazate", "essaouira", "taroudant", "khouribga", "beni mellal",
                "mexico", "moscou", "monaco", "nairobi", "oslo", "ottoman", "paris", "pekin", "prague", "quebec",
                "rabat", "rome", "tanger",
                "seoul", "stockholm", "tokyo", "tunis", "vienne", "washington", "yaounde", "zurich"));

        FALLBACK_WORDS.put("pays", List.of(
                "afghanistan", "algerie", "allemagne", "andorre", "angola", "argentine", "australie", "autriche",
                "belgique",
                "bresil", "bulgarie", "canada", "chili", "chine", "chypre", "danemark", "egypte", "espagne", "estonie",
                "ethiopie",
                "finlande", "france", "gabon", "grece", "guinee", "hongrie", "inde", "irlande", "islande", "italie",
                "japon",
                "jordanie", "kenya", "koweit", "liban", "luxembourg", "madagascar", "maroc", "mexique", "monaco",
                "norvege",
                "oman", "pakistan", "pays-bas", "perou", "pologne", "portugal", "qatar", "roumanie", "russie",
                "senegal",
                "suede", "suisse", "syrie", "tchad", "tunisie", "turquie", "ukraine", "uruguay", "vietnam", "yemen",
                "zambie"));

        FALLBACK_WORDS.put("animal", List.of(
                "abeille", "aigle", "ane", "antilope", "araignee", "baleine", "belette", "boeuf", "bouc", "canard",
                "cerf",
                "chat", "cheval", "chien", "cochon", "coq", "cygne", "dauphin", "dromadaire", "ecureuil", "elephant",
                "faucon", "fourmi", "girafe", "gorille", "grenouille", "guepard", "hibou", "hippopotame", "hirondelle",
                "iguane", "jaguar", "kangourou", "koala", "lapin", "leopard", "lezard", "lion", "loup", "marmotte",
                "mouton",
                "oie", "oiseau", "ours", "panda", "panthere", "papillon", "perroquet", "phoque", "pigeon", "poisson",
                "poule", "puma", "python",
                "rat", "renard", "requin", "rhinocéros", "sanglier", "serpent", "singe", "souris", "tigre", "tortue",
                "vache", "veau", "zebre"));

        FALLBACK_WORDS.put("métier", List.of(
                "acteur", "agriculteur", "architecte", "artiste", "avocat", "boucher", "boulanger", "caissier",
                "chauffeur",
                "chef", "chirurgien", "coiffeur", "comptable", "dentiste", "dessinateur", "docteur", "ecrivain",
                "electricien",
                "enseignant", "facteur", "fermier", "gardien", "infirmier", "ingenieur", "instituteur", "jardinier",
                "journaliste",
                "juge", "mecanicien", "medecin", "menuisier", "militaire", "musicien", "notaire", "opticien", "peintre",
                "pharmacien", "pilote", "plombier", "policier", "pompier", "professeur", "psychologue", "reparateur",
                "secretaire", "serveur", "soldat", "tailleur", "veterinaire", "webmaster", "wagonnier"));

        FALLBACK_WORDS.put("prénom", List.of(
                "alice", "anne", "arthur", "axel", "beatrice", "benjamin", "camille", "claire", "david", "denis",
                "elodie", "aya", "ahmed", "amine", "ali", "anis", "anouar", "asma", "amal", "achraf", "adam",
                "emma", "eric", "fabrice", "florence", "gabriel", "guillaume", "helene", "hugo", "ines", "isabelle",
                "jean", "fatima", "fatoumata", "fouad", "farid", "faycal", "fatiha", "fadel",
                "julie", "karine", "kevin", "laura", "louis", "marc", "marie", "nicolas", "noemi", "olivier", "paul",
                "pierre", "mohamed", "mustapha", "mouna", "meriem", "mehdi", "myriam", "mourad",
                "quentin", "raphael", "sarah", "sophie", "thomas", "ulysse", "ursule", "ugo", "umar", "urielle",
                "ulrich",
                "valerie", "victor", "william", "xavier",
                "yann", "yassine", "youssef", "yahya", "younes", "yasmina", "yasmine", "youness", "zoe", "zakaria",
                "zaynab"));

        FALLBACK_WORDS.put("marque", List.of(
                "adidas", "apple", "audi", "bmw", "chanel", "cocacola", "danone", "dell", "disney", "ebay", "ford",
                "google",
                "gucci", "h&m", "honda", "hp", "ikea", "intel", "jordan", "lacoste", "lego", "levis", "lg", "loreal",
                "lv",
                "mcdonalds", "mercedes", "microsoft", "nespresso", "nike", "nintendo", "nokia", "nvidia", "orange",
                "panasonic",
                "peugeot", "philips", "puma", "renault", "rolex", "samsung", "sony", "starbucks", "tesla", "toyota",
                "uber",
                "volkswagen", "zara", "zoom"));

        FALLBACK_WORDS.put("objet", List.of(
                "agenda", "allumette", "ampoule", "ancre", "anneau", "armoire", "assiette", "avion", "balai", "ballon",
                "banc", "bateau", "biberon", "bijou", "boite", "bouteille", "brosse", "bureau", "cadenas", "cadre",
                "cahier",
                "camera", "canapé", "carte", "ceinture", "chaise", "chapeau", "chaussure", "cle", "cloche", "clou",
                "coffre",
                "collier", "couteau", "crayon", "cuillere", "dictionnaire", "disque", "drapeau", "echelle", "ecran",
                "epee",
                "eponge", "etagere", "fauteuil", "fenetre", "fer", "filet", "flacon", "fourche", "gant", "gateau",
                "gourde",
                "grille", "guitare", "horloge", "housse", "image", "instrument", "interrupteur", "jante", "jarre",
                "jouet",
                "journal", "jumelles", "kepi", "keyboard", "kiwi", "lacets", "lampe", "lanterne", "lecteur", "lentille",
                "levier", "lime", "lit", "livre", "loupe", "lunettes", "machine", "maillot", "mallette", "manteau",
                "marteau",
                "masque", "medaille", "miroir", "mobile", "moniteur", "montre", "mouchoir", "nappe", "navire",
                "nettoyeur",
                "niveau", "noeud", "note", "ordinateur", "oreiller", "outil", "ouvrage", "palan", "panier", "papier",
                "parapluie", "parcours", "passoire", "peigne", "pendule", "perceuse", "pince", "pinceau", "pipe",
                "pistolet",
                "pivots", "plaque", "plateau", "plume", "pneu", "poele", "poignee", "pointeur", "pompe", "portail",
                "porte",
                "pot", "poubelle", "poutre", "prise", "projecteur", "pupitre", "pyjama", "quille", "rabot", "radar",
                "radio",
                "raquette", "rasoir", "rayon", "recepteur", "reflecteur", "registre", "reille", "relais", "remorque",
                "repartiteur",
                "reservoir", "ressort", "rideau", "ring", "robinet", "robot", "roche", "rondelle", "roue", "routeur",
                "ruban",
                "sablier", "sac", "saladier", "sandwich", "sangle", "sapin", "satellite", "seau", "seche-cheveux",
                "secteur",
                "selle", "serpillere", "serrure", "serviette", "siege", "sifflet", "sirop", "ski", "socle", "soie",
                "sommier",
                "sonnaille", "sonnette", "sorbetiere", "soufflerie", "soupape", "source", "soutien-gorge", "spatule",
                "sphère",
                "spirale", "stabilisateur", "statuette", "store", "stylo", "support", "surface", "tablier", "tabouret",
                "tapis",
                "tasse", "telephone", "televiseur", "tenaille", "tente", "terrasse", "thermometre", "tige", "timbre",
                "tiroir",
                "tissu", "toile", "tondeuse", "torche", "toupie", "tour", "tournevis", "traceur", "train", "traineau",
                "transmission",
                "treuil", "tringle", "tripode", "tronconneuse", "trop-plein", "trou", "trousse", "tube", "tuner",
                "tuyau",
                "uniforme", "unite", "urne", "ustensile", "usine", "vaisseau", "valise", "valve", "ventilateur",
                "verre",
                "verrou", "vêtement", "vibreur", "vilebrequin", "vis", "viseur", "voiture", "volet", "volimetre",
                "wagon",
                "x-ray", "xylophone", "yacht", "yaourt", "yo-yo", "zinc", "zone"));

        FALLBACK_WORDS.put("célébrité", List.of(
                "adele", "aristote", "beyonce", "brad pitt", "charles de gaulle", "da vinci", "einstein",
                "elvis presley",
                "federer", "gandhi", "hugo", "jackie chan", "jackson", "julie césar", "justin bieber", "kanye west",
                "lady gaga", "leonardo dicaprio", "macron", "madonna", "maradona", "messi", "michael jordan", "mozart",
                "nelson mandela", "obama", "oprah winfrey", "picasso", "ronaldo", "shakira", "steve jobs", "trump",
                "walt disney", "zidane"));
    }

    private static final Map<String, Set<String>> CATEGORY_BLACKLIST = Map.of(
            "objet", Set.of("mama", "papa", "bebe"),
            "animal", Set.of("xmabd", "abcd", "efgh"),
            "pays", Set.of("azer", "tyui"),
            "ville", Set.of("opqr", "lmno"));

    public static class ValidationResult implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public boolean isValid;
        public String message;
        public int score;

        public ValidationResult(boolean isValid, String message, int score) {
            this.isValid = isValid;
            this.message = message;
            this.score = score;
        }

        public static ValidationResult valid(String msg, int baseScore) {
            return new ValidationResult(true, msg, baseScore);
        }

        public static ValidationResult invalid(String msg) {
            return new ValidationResult(false, msg, 0);
        }
    }

    public ValidationResult validateWord(String category, String word, String letter, String language) {
        System.out.println("=== DEBUG VALIDATION ===");
        System.out.println("Mot reçu: '" + word + "'");
        System.out.println("Catégorie: " + category);
        System.out.println("Lettre attendue: " + letter);

        if (word == null) {
            System.out.println("❌ REJETÉ: Nul");
            return ValidationResult.invalid("Vide");
        }
        String cleanWord = word.trim();
        if (cleanWord.isEmpty()) {
            System.out.println("❌ REJETÉ: Vide");
            return ValidationResult.invalid("Vide");
        }

        // 1. Local Checks
        if (cleanWord.length() < 2) {
            System.out.println("❌ REJETÉ: Trop court");
            return ValidationResult.invalid("Trop court");
        }

        // Strict start letter check (case insensitive)
        if (!cleanWord.toUpperCase().startsWith(letter.toUpperCase())) {
            System.out.println("❌ REJETÉ: Mauvaise lettre (Attendu: " + letter + ", A: " + cleanWord.charAt(0) + ")");
            return ValidationResult.invalid("Mauvaise lettre");
        }

        // Regex: Letters, accents, hyphens, spaces only
        if (!cleanWord.matches("[a-zA-ZÀ-ÿ\\-\\s]+")) {
            System.out.println("❌ REJETÉ: Caractères invalides");
            return ValidationResult.invalid("Caractères invalides");
        }

        String lowerWord = cleanWord.toLowerCase();
        if (INVALID_WORDS.contains(lowerWord)) {
            System.out.println("❌ REJETÉ: Mot interdit");
            return ValidationResult.invalid("Mot interdit");
        }

        // Calculate score with bonuses
        int baseScore = 5;
        if (cleanWord.length() >= 10)
            baseScore += 3;
        else if (cleanWord.length() >= 8)
            baseScore += 2;
        else if (cleanWord.length() >= 6)
            baseScore += 1;
        int finalScore = Math.min(baseScore, 8);

        // Simple local whitelist/fallback for common words (Only for French for now)
        if ("fr".equalsIgnoreCase(language)) {
            List<String> fbWords = FALLBACK_WORDS.get(category.toLowerCase());
            if (fbWords != null && fbWords.contains(lowerWord)) {
                System.out.println("✅ ACCÉPTÉ (Fallback Local FR): " + cleanWord + " | Score: " + finalScore);
                return ValidationResult.valid("Validé localement", finalScore);
            }
        }

        // 2. Check Cache (Language-aware)
        ValidatedWord cached = getFromCache(category, lowerWord, language);
        if (cached != null) {
            if (cached.isValid()) {
                System.out.println("✅ ACCÉPTÉ (Cache): " + cleanWord + " | Score: " + finalScore);
                return ValidationResult.valid("Validé (Cache)", finalScore);
            } else {
                System.out.println("❌ REJETÉ (Cache): " + cleanWord);
                return ValidationResult.invalid("Invalide (Cache)");
            }
        }

        // 3. Call DeepSeek API
        System.out.println("[DEBUG] Calling DeepSeek API for validation...");
        return callDeepSeek(category, cleanWord, letter, language, finalScore);
    }

    private ValidatedWord getFromCache(String category, String word, String language) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session
                    .createQuery("FROM ValidatedWord WHERE word = :word AND categoryName = :cat AND language = :lang",
                            ValidatedWord.class)
                    .setParameter("word", word.toLowerCase().trim())
                    .setParameter("cat", category.toLowerCase())
                    .setParameter("lang", language.toLowerCase())
                    .uniqueResult();
        } catch (Exception e) {
            System.err.println("Erreur Cache DB: " + e.getMessage());
            return null;
        }
    }

    private void saveToCache(String category, String word, boolean isValid, String source, String language) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            // 1. Sauvegarder le mot validé
            ValidatedWord vw = new ValidatedWord(word, category.toLowerCase(), isValid, source, language);
            session.save(vw);

            // 2. Si valide, synchroniser le compteur de la catégorie
            if (isValid) {
                try {
                    com.bacplus.models.Category catEntity = session
                            .createQuery("FROM Category WHERE lower(name) = :name", com.bacplus.models.Category.class)
                            .setParameter("name", category.toLowerCase().trim())
                            .uniqueResult();

                    if (catEntity != null) {
                        catEntity.setWordCount(catEntity.getWordCount() + 1);
                        session.update(catEntity);
                        System.out.println("[DB-SYNC] Compteur incrémenté pour: " + category);
                    }
                } catch (Exception catEx) {
                    System.err
                            .println("⚠️ Impossible de mettre à jour le compteur de catégorie: " + catEx.getMessage());
                }
            }

            tx.commit();
        } catch (Exception e) {
            System.err.println("Erreur Sauvegarde Cache: " + e.getMessage());
        }
    }

    private ValidationResult callDeepSeek(String category, String word, String letter, String language,
            int scoreToReturn) {
        System.out.println("[DeepSeek] Appel API pour: " + word);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + apiKey);

            String langName = "fr".equalsIgnoreCase(language) ? "français" : "anglais";
            String translatedCat = getCategoryInLanguage(category, language);
            System.out.println("[DEBUG] callDeepSeek - langName: " + langName + ", category: " + translatedCat
                    + " (origin: " + category + "), letter: "
                    + letter + ", word: " + word);

            String systemPrompt = String.format(
                    """
                            🔍 **SYSTÈME DE VALIDATION PRÉCIS POUR BACCALAUREAT+**

                            # RÔLE
                            Tu es un validateur de mots **PRÉCIS MAIS RAISONNABLE** pour le jeu Baccalaureat+.

                            # RÈGLES CLAIRES ET PRÉCISES

                            ## 1. LANGUE - CRITÈRE STRICT
                            ✅ **ACCEPTE UNIQUEMENT** les mots qui existent en **%s**
                            ❌ REFUSE les mots qui n'existent qu'en d'autres langues

                            EXCEPTION SPÉCIFIQUE :
                            • Pour la catégorie **"PRÉNOM"** uniquement : Accepte les prénoms de TOUTES les cultures
                              Exemples acceptés : Mohamed (arabe), Wei (chinois), Jean (français), Maria (espagnol)

                            ## 2. CATÉGORIE - INTERPRÉTATION PRÉCISE
                            CATÉGORIE À VALIDER : **%s**

                            RÈGLES PAR CATÉGORIE :

                            • **"PRÉNOM"** → ✅ Prénoms de TOUTES cultures (arabe, français, anglais, asiatique, africain...)
                                              ✅ Noms personnels reconnus internationalement

                            • **"PAYS"** → ✅ Noms de pays en **%s** (France, Maroc, Espagne...)
                                           ❌ Noms dans d'autres langues (Germany au lieu de Allemagne)

                            • **"VILLE"** → ✅ Noms de villes en **%s** (Paris, Casablanca, Londres...)
                                            ✅ Villes internationales avec leur nom local

                            • **"ANIMAL"** → ✅ Noms d'animaux en **%s** (Lion, Chat, Éléphant...)
                                             ❌ Noms scientifiques latins

                            • **"MÉTIER"** → ✅ Professions en **%s** (Médecin, Ingénieur, Enseignant...)

                            • **"MARQUE"** → ✅ Marques commerciales connues

                            • **"OBJET"** → ✅ Objets en **%s** (Chaise, Livre, Téléphone...)

                            • **"CÉLÉBRITÉ"** → ✅ Personnes célèbres connues internationalement

                            ## 3. LETTRE - CRITÈRE ABSOLU
                            ✅ Le mot DOIT COMMENCER par la lettre : **%s**
                            ❌ Rejette immédiatement si première lettre différente

                            ## 4. VÉRIFICATION RÉALITÉ
                            ✅ Le mot doit exister/référencer quelque chose de réel
                            ❌ Pas de mots inventés, fantaisistes ou aléatoires

                            # MOT À VALIDER : **"%s"**

                            # DÉCISION PAR DÉFAUT
                            • **En cas de doute** → Vérifie si le mot existe en %s
                            • **Si existence confirmée** → Accepte
                            • **Si inexistant** → Refuse

                            # FORMAT DE RÉPONSE
                            {
                              "valide": true/false,
                              "raison": "explication courte en %s"
                            }

                            # EXEMPLES D'ACCEPTATION
                            • "Mohamed" pour "PRÉNOM" → ACCEPTE (prénom arabe international)
                            • "Paris" pour "VILLE" → ACCEPTE (ville française)
                            • "Maroc" pour "PAYS" → ACCEPTE (pays en français)
                            • "Chat" pour "ANIMAL" → ACCEPTE (animal en français)

                            # EXEMPLES DE REJET
                            • "Germany" pour "PAYS" → REFUSE (doit être "Allemagne" en français)
                            • "Book" pour "OBJET" → REFUSE (doit être "Livre" en français)
                            • "Xmabd" pour n'importe quoi → REFUSE (mot inventé)
                            • "Paris" avec lettre "M" → REFUSE (commence par P, pas M)
                            """,
                    langName,
                    translatedCat,
                    langName,
                    langName,
                    langName,
                    langName,
                    langName,
                    letter,
                    word,
                    langName,
                    langName);

            String jsonBody = mapper.createObjectNode()
                    .put("model", "deepseek-chat")
                    .set("messages", mapper.createArrayNode()
                            .add(mapper.createObjectNode().put("role", "system").put("content", systemPrompt)))
                    .toString();

            post.setEntity(new StringEntity(jsonBody, "UTF-8"));

            try (CloseableHttpResponse response = client.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode != 200) {
                    System.out.println("⚠️ API Erreur HTTP " + statusCode + ", fallback validation local basique.");
                    return ValidationResult.invalid("Service indisponible");
                }

                JsonNode root = mapper.readTree(responseBody);
                if (root.has("choices") && root.get("choices").size() > 0) {
                    String content = root.get("choices").get(0).get("message").get("content").asText();
                    System.out.println("[DeepSeek] Raw Response Content: " + content);

                    // Harden JSON extraction
                    if (content.contains("{")) {
                        content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
                    }
                    content = content.replaceAll("```json", "").replaceAll("```", "").trim();

                    JsonNode resultNode = mapper.readTree(content);
                    boolean valid = resultNode.has("valide") && resultNode.get("valide").asBoolean();
                    String reason = resultNode.has("raison") ? resultNode.get("raison").asText() : "";

                    saveToCache(category, word, valid, "DEEPSEEK", language);
                    System.out.println("API Résultat: " + (valid ? "✅ " : "❌ ") + reason);
                    return valid ? ValidationResult.valid(reason, scoreToReturn) : ValidationResult.invalid(reason);
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur API DeepSeek: " + e.getMessage());
        }
        return ValidationResult.invalid("Erreur réseau");
    }

    public String suggestWord(String category, String letter, String language) {
        System.out.println(
                "[Suggestion] Seeking word for " + category + " starting with " + letter + " (" + language + ")");

        // 1. PRIMARY: Try DeepSeek API for fresh and diverse suggestions
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + apiKey);

            String langName = "en".equalsIgnoreCase(language) ? "anglais" : "français";
            String translatedCat = getCategoryInLanguage(category, language);

            String prompt = String.format(
                    "Tu es un expert du jeu Baccalaureat. Donne-moi UN SEUL mot original et vrai en %s pour la catégorie '%s' commençant par la lettre '%s'. "
                            + "N'hésite pas à proposer des noms propres, prénoms arabes ou villes internationales (ex: Agadir, Fatima, etc. si applicable). "
                            + "Réponds juste le mot, sans ponctuation.",
                    langName, translatedCat, letter);

            String jsonBody = mapper.createObjectNode()
                    .put("model", "deepseek-chat")
                    .set("messages", mapper.createArrayNode()
                            .add(mapper.createObjectNode().put("role", "user").put("content", prompt)))
                    .toString();

            post.setEntity(new StringEntity(jsonBody, "UTF-8"));

            try (CloseableHttpResponse response = client.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode root = mapper.readTree(responseBody);
                if (root.has("choices") && root.get("choices").size() > 0) {
                    String suggestion = root.get("choices").get(0).get("message").get("content").asText().trim();
                    suggestion = suggestion.replaceAll("[^\\p{L}\\s-]", "");
                    if (!suggestion.isEmpty() && suggestion.toUpperCase().startsWith(letter.toUpperCase())) {
                        System.out.println("✅ Suggestion (API): " + suggestion);
                        return suggestion;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ API Suggestion Error: " + e.getMessage());
        }

        // 2. FALLBACK: Use local randomized list if API fails
        if ("fr".equalsIgnoreCase(language)) {
            List<String> tailWords = FALLBACK_WORDS.get(category.toLowerCase());
            if (tailWords != null) {
                List<String> matches = tailWords.stream()
                        .filter(w -> w.toUpperCase().startsWith(letter.toUpperCase()))
                        .collect(Collectors.toList());
                if (!matches.isEmpty()) {
                    Collections.shuffle(matches);
                    String match = matches.get(0);
                    System.out.println("✅ Suggestion (Local Fallback - Randomized): " + match);
                    return match;
                }
            }
        }

        return letter.toUpperCase() + "...";
    }

    public static long getWordCountForCategory(String categoryName) {
        if (categoryName == null)
            return 0;
        String cleanCat = categoryName.toLowerCase().trim();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return (Long) session
                    .createQuery(
                            "SELECT count(w) FROM ValidatedWord w WHERE lower(w.categoryName) = :cat AND w.isValid = true")
                    .setParameter("cat", cleanCat)
                    .uniqueResult();
        } catch (Exception e) {
            System.err.println("Erreur comptage mots pour [" + cleanCat + "]: " + e.getMessage());
            return 0;
        }
    }
}
