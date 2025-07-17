package main;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GetGamesStats {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public static void main(String[] args) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            //Fetching all available seasons
            List<String> seasonCodes = fetchSeasonsWithRetry(mapper);
            //Processing each season
            for (String seasonCode : seasonCodes) {
                //Creating directories for data collection
                String gamesDir = "games_" + seasonCode;
                String statsDir = "stats_" + seasonCode;
                new File(gamesDir).mkdirs();
                new File(statsDir).mkdirs();
                //Fetching game codes
                List<Integer> gameCodes = fetchGameCodesWithRetry(mapper, seasonCode);
                if (gameCodes.isEmpty()) {
                    System.err.println("No games for " + seasonCode);
                    continue;
                }
                for (int code : gameCodes) {
                    System.out.println("[" + seasonCode + "] Fetching game with code: " + code);
                    //Collecting game data for each game
                    fetchGameWithRetry(mapper, seasonCode, code, gamesDir, statsDir);
                    Thread.sleep(500);
                }
            }
        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> fetchSeasonsWithRetry(ObjectMapper mapper) throws Exception {
        String seasonsUrl = "https://api-live.euroleague.net/v2/competitions/E/seasons";

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            try {
                HttpRequest seasonsReq = HttpRequest.newBuilder()
                        .uri(URI.create(seasonsUrl))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .build();
                HttpResponse<String> seasonsRes = client.send(seasonsReq, HttpResponse.BodyHandlers.ofString());

                if (seasonsRes.statusCode() == 200) {
                    String seasonsJson = seasonsRes.body();
                    File seasonsFile = new File("seasons.json");
                    if (!seasonsFile.exists()) {
                        try {
                            Object seasonsObj = mapper.readValue(seasonsJson, Object.class);
                            String prettySeasonsJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(seasonsObj);

                            try (FileWriter fw = new FileWriter("seasons.json")) {
                                fw.write(prettySeasonsJson);
                            }

                            System.out.println("Seasons data saved to seasons.json");
                        } catch (Exception fileWriteException) {
                            System.err.println("Warning: Failed to save seasons data to file: " + fileWriteException.getMessage());
                        }
                    }
                    JsonNode seasonsData = mapper.readTree(seasonsRes.body()).path("data");
                    if (!seasonsData.isArray() || seasonsData.isEmpty()) {
                        throw new RuntimeException("No seasons data found");
                    }

                    List<String> seasonCodes = new ArrayList<>();
                    for (JsonNode season : seasonsData) {
                        String code = season.path("code").asText();
                        if (!code.isEmpty()) {
                            seasonCodes.add(code);
                        }
                    }
                    return seasonCodes;
                } else if (seasonsRes.statusCode() == 429) {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } else {
                    throw new RuntimeException("Failed to fetch seasons: " + seasonsRes.statusCode());
                }
            } catch (Exception e) {
                System.out.println("Seasons fetch attempt " + attempt + " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw e;
                }
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }

        return new ArrayList<>();
    }

    private static List<Integer> fetchGameCodesWithRetry(ObjectMapper mapper, String seasonCode) {
        String listUrl = String.format("https://api-live.euroleague.net/v2/competitions/E/seasons/%s/games", seasonCode);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            try {
                HttpRequest listReq = HttpRequest.newBuilder()
                        .uri(URI.create(listUrl))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .build();
                HttpResponse<String> listRes = client.send(listReq, HttpResponse.BodyHandlers.ofString());

                if (listRes.statusCode() == 200) {
                    String listJson = listRes.body();
                    JsonNode dataArray = mapper.readTree(listJson).path("data");
                    List<Integer> gameCodes = new ArrayList<>();

                    if (dataArray.isArray()) {
                        for (JsonNode gNode : dataArray) {
                            int code = gNode.path("gameCode").asInt();
                            if (code > 0) {
                                gameCodes.add(code);
                            }
                        }
                    }
                    return gameCodes;
                } else if (listRes.statusCode() == 429) {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } else {
                    System.err.println("Failed to fetch games for " + seasonCode + ": " + listRes.statusCode());
                    break;
                }
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    System.err.println("Failed to fetch games for " + seasonCode + " after " + MAX_RETRIES + " attempts");
                    break;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return new ArrayList<>();
    }

    private static void fetchGameWithRetry(ObjectMapper mapper, String seasonCode,
                                           int code, String gamesDir, String statsDir) {
        String gameUrl = String.format(
                "https://api-live.euroleague.net/v2/competitions/E/seasons/%s/games/%d",
                seasonCode, code);
        String statsUrl = gameUrl + "/stats";

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)  
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> gameRes = client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(gameUrl))
                                .header("Accept", "application/json")
                                .timeout(Duration.ofSeconds(60))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                String gameJson = gameRes.body();
                if (gameRes.statusCode() != 200 || gameJson.isBlank()) {
                    if (gameRes.statusCode() == 429) {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                        continue;
                    } else {
                        System.err.println("Failed to fetch game " + code + " (HTTP " + gameRes.statusCode() + ")");
                        return;
                    }
                }

                Thread.sleep(200); 

                HttpResponse<String> statsRes = client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(statsUrl))
                                .header("Accept", "application/json")
                                .timeout(Duration.ofSeconds(60))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                String statsJson = statsRes.body();
                if (statsRes.statusCode() != 200 || statsJson.isBlank()) {
                    if (statsRes.statusCode() == 429) {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                        continue;
                    } else {
                        System.err.println("Failed to fetch stats " + code + " (HTTP " + statsRes.statusCode() + ")");
                        return;
                    }
                }

                Files.writeString(Path.of(gamesDir, "game_" + code + ".json"),
                        mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(mapper.readTree(gameJson)));

                Files.writeString(Path.of(statsDir, "stats_" + code + ".json"),
                        mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(mapper.readTree(statsJson)));
                return; 

            } catch (Exception e) {
                System.out.println("Attempt " + attempt + " failed for game " + code +
                        ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                if (attempt == MAX_RETRIES) {
                    System.err.println("Failed to fetch game " + code + " after " + MAX_RETRIES + " attempts");
                    return;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
