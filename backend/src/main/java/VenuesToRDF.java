import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VenuesToRDF {
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

        File[] files = gamesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files found in games directory for season " + seasonId);
            return;
        }

        Set<String> seenVenueCodes = new HashSet<>();
        String outputFile = OUTPUT_DIR + "/venues" + seasonId + ".ttl";

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Header prefixes
            writer.println("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            writer.println("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .");
            writer.println("@prefix bball: <http://www.ics.forth.gr/isl/Basketball#> .");
            writer.println("@prefix ent: <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            writer.println("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n");

            int venueCount = 0;
            for (File file : files) {
                try {
                    JsonNode root = mapper.readTree(file);
                    JsonNode venueNode = root.path("venue");

                    if (venueNode.isMissingNode() || venueNode.isNull()) {
                        continue;
                    }

                    String code = venueNode.path("code").asText("");
                    if (code.isEmpty()) {
                        continue;
                    }

                    if (seenVenueCodes.contains(code)) {
                        continue;
                    }

                    seenVenueCodes.add(code);

                    String name = venueNode.path("name").asText("");
                    int capacity = venueNode.path("capacity").asInt(0);
                    String address = venueNode.path("address").asText("");

                    writer.println("ent:" + code + " a bball:Venue;");
                    writer.println("    rdfs:label \"" + escape(name) + "\" ;");
                    writer.println("    bball:hasCode \"" + code + "\"" + (capacity > 0 || !address.isEmpty() ? " ;" : " ."));

                    if (capacity > 0) {
                        writer.println("    bball:hasCapacity \"" + capacity + "\"^^xsd:integer" + (!address.isEmpty() ? " ;" : " ."));
                    }

                    if (!address.isEmpty()) {
                        writer.println("    bball:hasAddress \"" + escape(address) + "\" .");
                    }

                    writer.println();
                    venueCount++;
                } catch (Exception e) {
                    System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
                }
            }
            System.out.println("RDF for " + venueCount + " venues from season " + seasonId + " exported to " + outputFile);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "'")
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