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

public class TeamsToRDF {
    private static final String BASE_DIR = ".";
    private static final String GAMES_PREFIX = "games";
    private static final String OUTPUT_DIR = "output";
    private static final String CLUB_ENDPOINT = "https://api-live.euroleague.net/v2/clubs/%s";
    private static final String CLUB_INFO_ENDPOINT = "https://api-live.euroleague.net/v2/clubs/%s/info";
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
        HttpClient http = HttpClient.newHttpClient();

        for (SeasonDirectory seasonDir : seasonDirs) {
            processGamesDirectory(seasonDir, mapper, http);
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

    private static void processGamesDirectory(SeasonDirectory seasonDir, ObjectMapper mapper, HttpClient http) throws Exception {
        File gamesDir = seasonDir.directory;
        String seasonId = seasonDir.seasonId;

        File[] files = gamesDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files in games directory for season " + seasonId);
            return;
        }

        Set<String> seen = new HashSet<>();
        String outputFile = OUTPUT_DIR + "/teams" + seasonId + ".ttl";

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // prefixes
            writer.println("@prefix rdf:      <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            writer.println("@prefix xsd:      <http://www.w3.org/2001/XMLSchema#> .");
            writer.println("@prefix bball:    <http://www.ics.forth.gr/isl/Basketball#> .");
            writer.println("@prefix ent:      <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            writer.println("@prefix euroleague:<https://www.euroleaguebasketball.net/euroleague/> .");
            writer.println("@prefix foaf:     <http://xmlns.com/foaf/0.1/> .");
            writer.println("@prefix skos:     <https://www.w3.org/TR/skos-reference/> .");
            writer.println("@prefix rdfs:     <http://www.w3.org/2000/01/rdf-schema#> .\n");

            int teamCount = 0;
            for (File f : files) {
                try {
                    JsonNode root = mapper.readTree(f);
                    for (String side : new String[]{"local", "road"}) {
                        JsonNode sideNode = root.path(side);
                        if (sideNode.isMissingNode() || sideNode.isNull()) {
                            continue;
                        }

                        JsonNode clubNode = sideNode.path("club");
                        if (clubNode.isMissingNode() || clubNode.isNull()) {
                            continue;
                        }

                        String code = clubNode.path("code").asText("");
                        if (code.isEmpty()) {
                            continue;
                        }

                        if (!seen.add(code)) {
                            continue; // Skip duplicates
                        }

                        // defaults
                        String name = "";
                        String alias = "";
                        String countryCode = "";
                        String venueCode = "";
                        String website = "";
                        String crest = "";
                        String comment = "";

                        // Fetch club details
                        try {
                            HttpRequest req1 = HttpRequest.newBuilder()
                                    .uri(URI.create(String.format(CLUB_ENDPOINT, code)))
                                    .GET().build();
                            HttpResponse<String> res1 = http.send(req1, HttpResponse.BodyHandlers.ofString());
                            if (res1.statusCode() == 200) {
                                JsonNode c = mapper.readTree(res1.body());
                                name = c.path("name").asText("");
                                alias = c.path("alias").asText("");

                                JsonNode countryNode = c.path("country");
                                if (!countryNode.isMissingNode() && !countryNode.isNull()) {
                                    countryCode = countryNode.path("code").asText("");
                                }

                                JsonNode venueNode = c.path("venue");
                                if (!venueNode.isMissingNode() && !venueNode.isNull()) {
                                    venueCode = venueNode.path("code").asText("");
                                }

                                website = c.path("website").asText("");

                                JsonNode imagesNode = c.path("images");
                                if (!imagesNode.isMissingNode() && !imagesNode.isNull()) {
                                    crest = imagesNode.path("crest").asText("");
                                }
                            } else {
                                System.err.println("Failed to fetch club details for " + code + " (status " + res1.statusCode() + ")");
                            }
                        } catch (Exception e) {
                            System.err.println("Error fetching club details for " + code + ": " + e.getMessage());
                        }

                        // Fetch club info
                        try {
                            HttpRequest req2 = HttpRequest.newBuilder()
                                    .uri(URI.create(String.format(CLUB_INFO_ENDPOINT, code)))
                                    .GET().build();
                            HttpResponse<String> res2 = http.send(req2, HttpResponse.BodyHandlers.ofString());
                            if (res2.statusCode() == 200) {
                                JsonNode info = mapper.readTree(res2.body());
                                comment = info.path("info").asText("");
                            } else {
                                System.err.println("Failed to fetch club info for " + code + " (status " + res2.statusCode() + ")");
                            }
                        } catch (Exception e) {
                            System.err.println("Error fetching club info for " + code + ": " + e.getMessage());
                        }

                        String subj = String.format(
                                "<https://www.euroleaguebasketball.net/euroleague/teams/-/%s>", code
                        );
                        writer.println(subj + " a bball:Team ;");
                        writer.printf("    bball:hasCode      \"%s\" ;%n", code);
                        writer.printf("    rdfs:label         \"%s\" ;%n", escape(alias.isEmpty() ? name : alias));

                        if (!alias.isEmpty() && !name.isEmpty() && !alias.equals(name)) {
                            writer.printf("    skos:altLabel      \"%s\" ;%n", escape(name));
                        }

                        if (!countryCode.isEmpty()) {
                            writer.printf("    bball:teamCountry  ent:%s ;%n", countryCode);
                        }

                        if (!venueCode.isEmpty()) {
                            writer.printf("    bball:teamVenue    ent:%s ;%n", venueCode);
                        }

                        if (!website.isEmpty()) {
                            writer.printf("    bball:hasWebsite   <%s> ;%n", website);
                        }

                        if (!crest.isEmpty()) {
                            writer.printf("    foaf:depiction     <%s> ;%n", crest);
                        }

                        if (!comment.isEmpty()) {
                            writer.printf("    rdfs:comment       \"%s\" ;%n", escape(comment));
                        }

                        writer.println("    .");
                        teamCount++;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file " + f.getName() + ": " + e.getMessage());
                }
            }
            System.out.println("RDF for " + teamCount + " teams from season " + seasonId + " exported to " + outputFile);
        }
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