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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CountriesToRDF {
    private static final String BASE_DIR = ".";
    private static final String STATS_PREFIX = "stats";
    private static final String OUTPUT_DIR = "output";
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

        Map<String, String> allCountries = new TreeMap<>();

        HttpClient http = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        for (SeasonDirectory seasonDir : seasonDirs) {
            Map<String, String> seasonCountries = processStatsDirectory(seasonDir, mapper, http);

            allCountries.putAll(seasonCountries);

            createCountriesTtl(seasonCountries, seasonDir.seasonId);
        }

        createCountriesTtl(allCountries, "all");
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

    private static Map<String, String> processStatsDirectory(SeasonDirectory seasonDir, ObjectMapper mapper, HttpClient http) throws Exception {
        File statsDir = seasonDir.directory;
        String seasonId = seasonDir.seasonId;

        System.out.println("Processing country data for season: " + seasonId);
        System.out.println("Stats directory: " + statsDir.getPath());

        // Keep results sorted by country code
        Map<String, String> countries = new TreeMap<>();

        File[] files = statsDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files found in stats directory for season " + seasonId);
            return countries;
        }

        for (File f : files) {
            try {
                JsonNode root = mapper.readTree(f);

                for (String side : new String[]{"local", "road"}) {
                    JsonNode sideNode = root.path(side);
                    if (sideNode.isMissingNode() || sideNode.isNull()) {
                        continue;
                    }

                    // Process coach country
                    JsonNode coachNode = sideNode.path("coach");
                    if (!coachNode.isMissingNode() && !coachNode.isNull()) {
                        String coachCode = coachNode.path("code").asText("");
                        if (!coachCode.isEmpty()) {
                            try {
                                HttpRequest req = HttpRequest.newBuilder()
                                        .uri(URI.create("https://api-live.euroleague.net/v2/people/" + coachCode))
                                        .GET()
                                        .build();

                                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                                if (resp.statusCode() == 200) {
                                    JsonNode coachInfo = mapper.readTree(resp.body());
                                    JsonNode cc = coachInfo.path("country");
                                    if (!cc.isMissingNode() && !cc.isNull()) {
                                        String code = cc.path("code").asText("");
                                        String name = cc.path("name").asText("");
                                        if (!code.isEmpty() && !name.isEmpty()) {
                                            countries.put(code, name);
                                        }
                                    }
                                } else {
                                    System.err.println("Failed to fetch coach " + coachCode + ": HTTP " + resp.statusCode());
                                }
                            } catch (Exception e) {
                                System.err.println("Error fetching coach " + coachCode + ": " + e.getMessage());
                            }
                        }
                    }

                    // Process players' countries
                    JsonNode players = sideNode.path("players");
                    if (players.isArray()) {
                        for (JsonNode entry : players) {
                            JsonNode playerNode = entry.path("player");
                            if (playerNode.isMissingNode() || playerNode.isNull()) {
                                continue;
                            }

                            JsonNode personNode = playerNode.path("person");
                            if (personNode.isMissingNode() || personNode.isNull()) {
                                continue;
                            }

                            JsonNode pCountry = personNode.path("country");
                            if (!pCountry.isMissingNode() && !pCountry.isNull()) {
                                String code = pCountry.path("code").asText("");
                                String name = pCountry.path("name").asText("");
                                if (!code.isEmpty() && !name.isEmpty()) {
                                    countries.put(code, name);
                                }
                            }

                            // Also collect birth countries if available
                            JsonNode birthCountry = personNode.path("birthCountry");
                            if (!birthCountry.isMissingNode() && !birthCountry.isNull()) {
                                String code = birthCountry.path("code").asText("");
                                String name = birthCountry.path("name").asText("");
                                if (!code.isEmpty() && !name.isEmpty()) {
                                    countries.put(code, name);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + f.getName() + ": " + e.getMessage());
            }
        }

        System.out.println("Found " + countries.size() + " countries for season " + seasonId);
        return countries;
    }

    private static void createCountriesTtl(Map<String, String> countries, String identifier) throws Exception {
        String outputFile = OUTPUT_DIR + "/countries" + (identifier.equals("all") ? "" : identifier) + ".ttl";

        try (PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {
            out.println("@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            out.println("@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .");
            out.println("@prefix bball: <http://www.ics.forth.gr/isl/Basketball#> .");
            out.println("@prefix ent:   <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            out.println("@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .");
            out.println();

            for (Map.Entry<String, String> e : countries.entrySet()) {
                String code = e.getKey();
                String name = toTitleCase(e.getValue());
                out.printf("ent:%s a bball:Country;%n", code);
                out.printf("    bball:hasCode \"%s\";%n", code);
                out.printf("    rdfs:label \"%s\".%n%n", escape(name));
            }
        }

        System.out.println("Wrote " + countries.size() + " countries to " + outputFile);
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(input.length());
        String[] words = input.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase());
            }
            if (i < words.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
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