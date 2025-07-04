import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class SeasonsToRDF {
    private static final String OUTPUT_DIR = "output"; // Output directory for TTL files
    private static final String SEASONS_JSON_FILE = "seasons.json"; // Input JSON file

    public static void main(String[] args) throws Exception {
        // Create output directory if it doesn't exist
        File outputDirectory = new File(OUTPUT_DIR);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // Check if seasons.json exists
        File seasonsFile = new File(SEASONS_JSON_FILE);
        if (!seasonsFile.exists()) {
            System.err.println("seasons.json not found. Please run GetSeasons.java first.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode root = mapper.readTree(seasonsFile);
            JsonNode dataArray = root.path("data");

            if (!dataArray.isArray() || dataArray.size() == 0) {
                System.err.println("No seasons data found in JSON");
                return;
            }

            createCombinedSeasonsFile(dataArray);
        } catch (Exception e) {
            System.err.println("Error processing seasons: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createCombinedSeasonsFile(JsonNode dataArray) throws Exception {
        String outputFile = OUTPUT_DIR + "/all_seasons.ttl";

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)))) {
            writeRDFPrefixes(writer);

            writer.println("## EuroLeague Seasons 2000-01 to 2024-25");
            writer.println();

            // Write league definition once
            writer.println("<https://www.euroleaguebasketball.net> rdf:type bball:League;");
            writer.println("\trdfs:label \"Euroleague\";");
            writer.println("\tbball:hasCode \"E\".");
            writer.println();

            // Write all seasons
            for (JsonNode seasonNode : dataArray) {
                String code = seasonNode.path("code").asText("");
                String alias = seasonNode.path("alias").asText("");
                int year = seasonNode.path("year").asInt(0);

                if (code.isEmpty() || alias.isEmpty()) {
                    continue;
                }

                String[] yearParts = alias.split("-");
                String startYear = String.valueOf(year);
                String endYear = yearParts.length > 1 ?
                        (year < 2000 ? "20" + yearParts[1] : String.valueOf(year + 1)) :
                        String.valueOf(year + 1);

                String seasonUri = "Season_" + alias.replace("-", "_");

                writer.println("<http://www.ics.forth.gr/isl/Basketball/entities/" + seasonUri + "> rdf:type bball:Season;");
                writer.println("\tbball:hasCode \"" + code + "\";");
                writer.println("\trdfs:label \"" + alias + "\" ;");
                writer.println("\tbball:hasLeague <https://www.euroleaguebasketball.net>;");
                writer.println("\tbball:startYear \"" + startYear + "\"^^xsd:gYear;");
                writer.println("\tbball:endYear \"" + endYear + "\"^^xsd:gYear.");
                writer.println();
            }
        }
    }

    private static void writeRDFPrefixes(PrintWriter writer) {
        writer.println("@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
        writer.println("@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .");
        writer.println("@prefix bball:      <http://www.ics.forth.gr/isl/Basketball#> .");
        writer.println("@prefix ent:        <http://www.ics.forth.gr/isl/Basketball/entities/> .");
        writer.println("@prefix euroleague: <https://www.euroleaguebasketball.net/euroleague/> .");
        writer.println("@prefix foaf:       <http://xmlns.com/foaf/0.1/> .");
        writer.println("@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .");
        writer.println("@prefix skos:       <https://www.w3.org/TR/skos-reference/> .");
        writer.println();
    }
}