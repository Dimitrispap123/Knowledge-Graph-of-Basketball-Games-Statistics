import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerToRDF {
    private static final String BASE_DIR = ".";
    private static final String STATS_PREFIX = "stats";
    private static final String OUTPUT_DIR = "output";
    private static final String BIO_ENDPOINT = "https://api-live.euroleague.net/v2/people/%s/bio";
    private static final Pattern SEASON_PATTERN = Pattern.compile("(\\d{4})");

    public static void main(String[] args) throws Exception {
        // Create output directory if it doesn't exist
        File outputDirectory = new File(OUTPUT_DIR);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // Find all stats directories
        File baseDir = new File(BASE_DIR);
        File[] allDirs = baseDir.listFiles(File::isDirectory);
        List<SeasonDirectory> seasonDirs = findStatsDirs(allDirs);

        if (seasonDirs.isEmpty()) {
            System.err.println("No stats directories found.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        HttpClient http = HttpClient.newHttpClient();

        for (SeasonDirectory seasonDir : seasonDirs) {
            processStatsDirectory(seasonDir, mapper, http);
        }
    }

    private static List<SeasonDirectory> findStatsDirs(File[] allDirs) {
        List<SeasonDirectory> statsDirs = new ArrayList<>();

        for (File dir : allDirs) {
            String name = dir.getName();
            if (name.startsWith(STATS_PREFIX)) {
                String seasonId = extractSeasonId(name);
                if (seasonId != null) {
                    statsDirs.add(new SeasonDirectory(dir, seasonId));
                }
            }
        }

        return statsDirs;
    }

    private static String extractSeasonId(String dirName) {
        Matcher matcher = SEASON_PATTERN.matcher(dirName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void processStatsDirectory(SeasonDirectory seasonDir, ObjectMapper mapper, HttpClient http) throws Exception {
        File statsDir = seasonDir.directory;
        String seasonId = seasonDir.seasonId;

        System.out.println("Processing player data for season: " + seasonId);
        System.out.println("Stats directory: " + statsDir.getPath());

        File[] files = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files found in stats folder for season " + seasonId);
            return;
        }

        Set<String> seenPlayerCodes = new HashSet<>();
        String outputFile = OUTPUT_DIR + "/players" + seasonId + ".ttl";

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // prefixes
            writer.println("@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            writer.println("@prefix bball: <http://www.ics.forth.gr/isl/Basketball#> .");
            writer.println("@prefix ent:   <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            writer.println("@prefix euroleague: <https://www.euroleaguebasketball.net/euroleague/> .");
            writer.println("@prefix foaf:  <http://xmlns.com/foaf/0.1/> .");
            writer.println("@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .");
            writer.println("@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n");

            int playerCount = 0;
            for (File file : files) {
                JsonNode root = mapper.readTree(file);
                for (String team : new String[]{"local", "road"}) {
                    JsonNode teamNode = root.path(team);
                    if (teamNode.isMissingNode() || teamNode.isNull()) {
                        continue;
                    }

                    JsonNode playersNode = teamNode.path("players");
                    if (playersNode.isMissingNode() || !playersNode.isArray()) {
                        continue;
                    }

                    for (JsonNode playerNode : playersNode) {
                        JsonNode playerObj = playerNode.path("player");
                        if (playerObj.isMissingNode() || playerObj.isNull()) {
                            continue;
                        }

                        JsonNode person = playerObj.path("person");
                        if (person.isMissingNode() || person.isNull()) {
                            continue;
                        }

                        String code = person.path("code").asText("");
                        if (code.isEmpty()) {
                            continue;
                        }

                        if (!seenPlayerCodes.add(code)) {
                            continue;  // skip duplicates
                        }

                        // basic info from the stats files
                        String uri = "https://www.euroleaguebasketball.net/euroleague/players/-/" + code;
                        String rawName = person.path("name").asText("");
                        String name = swapName(rawName);

                        // Handle potentially missing data
                        String country = person.path("country").path("code").asText("UNKNOWN");
                        String birthCountry = person.path("birthCountry").path("code").asText("UNKNOWN");
                        String position = playerObj.path("positionName").asText("");
                        double height = person.path("height").asDouble(0) / 100;
                        int weight = person.path("weight").asInt(0);

                        String birthDate = "";
                        JsonNode birthDateNode = person.path("birthDate");
                        if (!birthDateNode.isMissingNode() && birthDateNode.isTextual()) {
                            String rawDate = birthDateNode.asText("");
                            if (!rawDate.isEmpty()) {
                                birthDate = rawDate.split("T")[0];
                            }
                        }

                        String img = playerObj.path("images").path("headshot").asText("");

                        // fetch bio & achievements from API
                        String bio = "";
                        String achievs = "";
                        try {
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create(String.format(BIO_ENDPOINT, code)))
                                    .GET().build();
                            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                            if (resp.statusCode() == 200) {
                                JsonNode bioNode = mapper.readTree(resp.body());
                                bio = bioNode.path("bio").asText("");
                                achievs = bioNode.path("achievements").asText("");
                            } else {
                                System.err.println("⚠️ Failed to fetch bio for " + code + " (status " + resp.statusCode() + ")");
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Error fetching bio for " + code + ": " + e.getMessage());
                        }

                        // Write player data to TTL
                        writer.println("<" + uri + "> a bball:Player ;");
                        writer.println("    bball:hasCode \"" + code + "\" ;");
                        writer.println("    rdfs:label    \"" + name + "\" ;");

                        if (!country.equals("UNKNOWN")) {
                            writer.println("    bball:hasCountry    ent:" + country + " ;");
                        }

                        if (!birthCountry.equals("UNKNOWN")) {
                            writer.println("    bball:wasBornIn     ent:" + birthCountry + " ;");
                        }

                        if (height > 0) {
                            writer.println("    bball:hasHeight     \"" + height + "\"^^xsd:double ;");
                        }

                        if (weight > 0) {
                            writer.println("    bball:hasWeight     \"" + weight + "\"^^xsd:double ;");
                        }

                        if (!birthDate.isEmpty()) {
                            writer.println("    bball:hasBirthDate  \"" + birthDate + "\"^^xsd:date ;");
                        }

                        if (!position.isEmpty()) {
                            writer.println("    bball:hasPosition   \"" + position + "\" ;");
                        }

                        if (!img.isEmpty()) {
                            writer.println("    foaf:depiction      <" + img + "> ;");
                        }

                        if (!bio.isEmpty()) {
                            writer.println("    bball:hasBiography  \"" + bio.replace("\"", "\\\"") + "\" ;");
                        }

                        if (!achievs.isEmpty()) {
                            writer.println("    bball:hasAchievements  \"" + achievs.replace("\"", "\\\"") + "\" ;");
                        }

                        writer.println("    .");
                        playerCount++;
                    }
                }
            }
            System.out.println("RDF for " + playerCount + " players from season " + seasonId + " exported to " + outputFile);
        }
    }

    private static String swapName(String name) {
        if (name == null || name.isBlank()) return "";
        String fullName;
        String[] parts = name.split(",", 2);
        if (parts.length < 2) {
            fullName = name.trim();
        } else {
            fullName = parts[1].trim() + " " + parts[0].trim();
        }
        return toTitleCase(fullName);
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

            // split on hyphens
            String[] parts = w.split("-");
            for (int j = 0; j < parts.length; j++) {
                String p = parts[j];
                if (p.matches("[IVXLCDM]+")) {
                    // leave all‐caps Roman numerals as-is
                    out.append(p);
                } else if (!p.isEmpty()) {
                    // normal title‐case: first char upper, rest lower
                    out.append(Character.toUpperCase(p.charAt(0)));
                    if (p.length() > 1) {
                        out.append(p.substring(1).toLowerCase());
                    }
                }
                if (j < parts.length - 1) {
                    out.append('-');
                }
            }

            if (i < words.length - 1) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    // Helper class to store a stats directory with its season ID
    private static class SeasonDirectory {
        final File directory;
        final String seasonId;

        public SeasonDirectory(File directory, String seasonId) {
            this.directory = directory;
            this.seasonId = seasonId;
        }
    }
}