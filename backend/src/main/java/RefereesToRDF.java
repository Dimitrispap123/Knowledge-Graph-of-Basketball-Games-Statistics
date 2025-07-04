import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RefereesToRDF {
    private static final String BASE_DIR = ".";
    private static final String GAMES_PREFIX = "games";
    private static final String OUTPUT_DIR = "output";
    private static final Pattern SEASON_PATTERN = Pattern.compile("(\\d{4})");

    public static void main(String[] args) throws Exception {
        // Create output directory if it doesn't exist
        File outputDirectory = new File(OUTPUT_DIR);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // Find all games directories
        File baseDir = new File(BASE_DIR);
        File[] allDirs = baseDir.listFiles(File::isDirectory);
        List<SeasonDirectory> seasonDirs = findGamesDirs(allDirs);

        if (seasonDirs.isEmpty()) {
            System.err.println("No games directories found.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        for (SeasonDirectory seasonDir : seasonDirs) {
            processGamesDirectory(seasonDir, mapper);
        }
    }

    private static List<SeasonDirectory> findGamesDirs(File[] allDirs) {
        List<SeasonDirectory> gamesDirs = new ArrayList<>();

        for (File dir : allDirs) {
            String name = dir.getName();
            if (name.startsWith(GAMES_PREFIX)) {
                String seasonId = extractSeasonId(name);
                if (seasonId != null) {
                    gamesDirs.add(new SeasonDirectory(dir, seasonId));
                }
            }
        }

        return gamesDirs;
    }

    private static String extractSeasonId(String dirName) {
        Matcher matcher = SEASON_PATTERN.matcher(dirName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void processGamesDirectory(SeasonDirectory seasonDir, ObjectMapper mapper) throws Exception {
        File gamesDir = seasonDir.directory;
        String seasonId = seasonDir.seasonId;

        System.out.println("Processing referee data for season: " + seasonId);
        System.out.println("Games directory: " + gamesDir.getPath());

        Map<String, JsonNode> refereesMap = new HashMap<>();

        File[] files = gamesDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files found in games directory for season " + seasonId);
            return;
        }

        // Collect referee nodes from each game JSON
        for (File file : files) {
            try {
                JsonNode root = mapper.readTree(file);
                for (int i = 1; i <= 4; i++) {
                    JsonNode r = root.path("referee" + i);
                    if (r.isObject() && !r.isEmpty()) {
                        String code = r.path("code").asText(null);
                        if (code != null && !code.isEmpty() && !refereesMap.containsKey(code)) {
                            refereesMap.put(code, r);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
            }
        }

        if (refereesMap.isEmpty()) {
            System.out.println("No referees found for season " + seasonId);
            return;
        }

        // Write TTL
        String outputFile = OUTPUT_DIR + "/referees" + seasonId + ".ttl";
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)))) {
            writer.println("@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            writer.println("@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .");
            writer.println("@prefix bball:      <http://www.ics.forth.gr/isl/Basketball#> .");
            writer.println("@prefix ent:        <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            writer.println("@prefix euroleague: <https://www.euroleaguebasketball.net/euroleague/> .");
            writer.println("@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .");
            writer.println();

            for (Map.Entry<String, JsonNode> e : refereesMap.entrySet()) {
                String code = e.getKey();
                JsonNode r = e.getValue();

                String rawName = r.path("name").asText("");
                String name = swapName(rawName);

                // Handle potentially missing country data
                String country = "";
                JsonNode countryNode = r.path("country");
                if (!countryNode.isMissingNode() && !countryNode.isNull()) {
                    country = countryNode.path("code").asText("");
                }

                writer.println("ent:" + code + " rdf:type bball:Referee;");
                writer.println("    bball:hasCode    \"" + code + "\";");
                writer.println("    rdfs:label       \"" + escape(name) + "\"" + (country.isEmpty() ? " ." : ";"));

                if (!country.isEmpty()) {
                    writer.println("    bball:hasCountry ent:" + country + " .");
                }

                writer.println();
            }

            System.out.println("RDF for " + refereesMap.size() + " referees from season " + seasonId + " exported to " + outputFile);
        }
    }

    private static String swapName(String name) {
        if (name == null || name.isBlank()) return "";
        String[] parts = name.split(",", 2);
        String full = (parts.length < 2)
                ? name.trim()
                : parts[1].trim() + " " + parts[0].trim();
        return toTitleCase(full);
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(input.length());
        String[] words = input.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            out.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) out.append(w.substring(1).toLowerCase());
            if (i < words.length - 1) out.append(' ');
        }
        return out.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    // Helper class to store a games directory with its season ID
    private static class SeasonDirectory {
        final File directory;
        final String seasonId;

        public SeasonDirectory(File directory, String seasonId) {
            this.directory = directory;
            this.seasonId = seasonId;
        }
    }
}