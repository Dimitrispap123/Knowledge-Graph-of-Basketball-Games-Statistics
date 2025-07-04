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

public class CoachesToRDF {
    private static final String BASE_DIR = ".";
    private static final String STATS_PREFIX = "stats";
    private static final String OUTPUT_DIR = "output";
    private static final String PEOPLE_ENDPOINT = "https://api-live.euroleague.net/v2/people/%s";
    private static final String BIO_ENDPOINT = "https://api-live.euroleague.net/v2/people/%s/bio";
    private static final Pattern SEASON_PATTERN = Pattern.compile("(\\d{4})");

    public static void main(String[] args) throws Exception {
        File outputDirectory = new File(OUTPUT_DIR);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

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

        System.out.println("Processing coach data for season: " + seasonId);

        File[] files = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files in stats directory for season " + seasonId);
            return;
        }

        Set<String> seen = new HashSet<>();
        String outputFile = OUTPUT_DIR + "/coaches" + seasonId + ".ttl";

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("@prefix rdf:       <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            writer.println("@prefix xsd:       <http://www.w3.org/2001/XMLSchema#> .");
            writer.println("@prefix bball:     <http://www.ics.forth.gr/isl/Basketball#> .");
            writer.println("@prefix ent:       <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            writer.println("@prefix euroleague:<https://www.euroleaguebasketball.net/euroleague/> .");
            writer.println("@prefix rdfs:      <http://www.w3.org/2000/01/rdf-schema#> .\n");

            int coachCount = 0;
            for (File f : files) {
                JsonNode root = mapper.readTree(f);

                for (String side : new String[]{"local", "road"}) {
                    JsonNode sideNode = root.path(side);
                    if (sideNode.isMissingNode() || sideNode.isNull()) {
                        continue;
                    }

                    JsonNode coach = sideNode.path("coach");
                    if (coach.isMissingNode() || coach.isNull()) {
                        continue;
                    }

                    String code = coach.path("code").asText("");
                    if (code.isEmpty()) {
                        continue;
                    }

                    if (!seen.add(code)) {
                        continue;
                    }

                    String nameNode = coach.path("name").asText("");
                    String label = swapName(nameNode);
                    String country = "";
                    String birthDate = "";
                    String bio = "";
                    String ach = "";

                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(String.format(PEOPLE_ENDPOINT, code)))
                                .GET().build();
                        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) {
                            JsonNode p = mapper.readTree(resp.body());
                            JsonNode countryNode = p.path("country");
                            if (!countryNode.isMissingNode() && !countryNode.isNull()) {
                                country = countryNode.path("code").asText("");
                            }

                            JsonNode birthDateNode = p.path("birthDate");
                            if (!birthDateNode.isMissingNode() && !birthDateNode.isNull() && birthDateNode.isTextual()) {
                                String rawDate = birthDateNode.asText("");
                                if (!rawDate.isEmpty()) {
                                    birthDate = rawDate.split("T")[0];
                                }
                            }
                        } else {
                            System.err.println("Failed to fetch details for coach " + code + " (status " + resp.statusCode() + ")");
                        }
                    } catch (Exception e) {
                        System.err.println("Error fetching details for coach " + code + ": " + e.getMessage());
                    }

                    // Fetch bio and achievements
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(String.format(BIO_ENDPOINT, code)))
                                .GET().build();
                        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) {
                            JsonNode b = mapper.readTree(resp.body());
                            bio = b.path("bio").asText("");
                            ach = b.path("achievements").asText("");
                        } else {
                            System.err.println("Failed to fetch bio for coach " + code + " (status " + resp.statusCode() + ")");
                        }
                    } catch (Exception e) {
                        System.err.println("Error fetching bio for coach " + code + ": " + e.getMessage());
                    }

                    String subj = String.format(
                            "<https://www.euroleaguebasketball.net/euroleague/players/-/%s>", code
                    );
                    writer.println(subj + " a bball:Coach ;");
                    writer.printf("    bball:hasCode      \"%s\" ;%n", code);
                    writer.printf("    rdfs:label         \"%s\" ;%n", escape(label));

                    if (!country.isEmpty()) {
                        writer.printf("    bball:hasCountry   ent:%s ;%n", country);
                        writer.printf("    bball:wasBornIn    ent:%s ;%n", country);
                    }
                    if (!birthDate.isEmpty()) {
                        writer.printf("    bball:hasBirthDate \"%s\"^^xsd:date ;%n", birthDate);
                    }
                    if (!bio.isEmpty()) {
                        writer.printf("    bball:hasBiography \"%s\" ;%n", escape(bio));
                    }
                    if (!ach.isEmpty()) {
                        writer.printf("    bball:hasAchievements \"%s\" ;%n", escape(ach));
                    }
                    writer.println("    .");
                    coachCount++;
                }
            }
            System.out.println("RDF for " + coachCount + " coaches from season " + seasonId + " exported to " + outputFile);
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
            out.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                out.append(w.substring(1).toLowerCase());
            }
            if (i < words.length - 1) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    private static class SeasonDirectory {
        final File directory;
        final String seasonId;

        public SeasonDirectory(File directory, String seasonId) {
            this.directory = directory;
            this.seasonId = seasonId;
        }
    }
}