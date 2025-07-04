import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameToRDF {
    private static final String BASE_DIR     = ".";
    private static final String GAMES_PREFIX = "games";
    private static final String STATS_PREFIX = "stats";
    private static final String OUTPUT_DIR   = "output";
    private static final String BASE_LEAGUE  = "https://www.euroleaguebasketball.net";
    private static final DecimalFormat DF1   = new DecimalFormat("0.0");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(\\d{4})");

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Create output directory if it doesn't exist
        File outputDirectory = new File(OUTPUT_DIR);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // Find all games directories
        File baseDir = new File(BASE_DIR);
        File[] allDirs = baseDir.listFiles(File::isDirectory);
        List<SeasonPair> seasonPairs = findSeasonPairs(allDirs);

        if (seasonPairs.isEmpty()) {
            System.err.println("No matching game/stats directory pairs found");
            return;
        }
        // Pairing game and stats JSON Dfiles for each season
        for (SeasonPair seasonPair : seasonPairs) {
            processSeasonPair(seasonPair, mapper);
        }
    }


    private static boolean shouldSkipGame(JsonNode game, JsonNode stats) {
        // Check if game was actually played
        boolean played = game.path("played").asBoolean(true); // default to true if missing
        if (!played) {
            return true;
        }

        // Check if stats are empty/null
        JsonNode localStats = stats.path("local");
        JsonNode roadStats = stats.path("road");

        // Check if coach, players, team, or total are null/empty
        boolean localEmpty = isStatsEmpty(localStats);
        boolean roadEmpty = isStatsEmpty(roadStats);

        if (localEmpty && roadEmpty) {
            return true;
        }

        // Additional check: if both teams have 0 score and no quarter scores
        int homeScore = game.path("local").path("score").asInt();
        int awayScore = game.path("road").path("score").asInt();

        if (homeScore == 0 && awayScore == 0) {
            JsonNode homePartials = game.path("local").path("partials");
            JsonNode awayPartials = game.path("road").path("partials");

            boolean homePartialsEmpty = arePartialsEmpty(homePartials);
            boolean awayPartialsEmpty = arePartialsEmpty(awayPartials);

            return homePartialsEmpty && awayPartialsEmpty;
        }

        return false;
    }


    private static boolean isStatsEmpty(JsonNode teamStats) {
        if (teamStats.isMissingNode()) {
            return true;
        }

        JsonNode coach = teamStats.path("coach");
        JsonNode players = teamStats.path("players");
        JsonNode team = teamStats.path("team");
        JsonNode total = teamStats.path("total");

        return (coach.isNull() || coach.isMissingNode()) &&
                (players.isArray() && players.size() == 0) &&
                (team.isNull() || team.isMissingNode()) &&
                (total.isNull() || total.isMissingNode());
    }

    private static boolean arePartialsEmpty(JsonNode partials) {
        if (partials.isMissingNode()) {
            return true;
        }

        int q1 = partials.path("partials1").asInt();
        int q2 = partials.path("partials2").asInt();
        int q3 = partials.path("partials3").asInt();
        int q4 = partials.path("partials4").asInt();

        JsonNode extraPeriods = partials.path("extraPeriods");
        boolean hasExtraTime = extraPeriods.isObject() && extraPeriods.size() > 0;

        return q1 == 0 && q2 == 0 && q3 == 0 && q4 == 0 && !hasExtraTime;
    }

    private static List<SeasonPair> findSeasonPairs(File[] allDirs) {
        List<SeasonPair> pairs = new ArrayList<>();
        List<File> gameDirs = new ArrayList<>();
        List<File> statsDirs = new ArrayList<>();

        // Separate game and stats directories
        for (File dir : allDirs) {
            String name = dir.getName();
            if (name.startsWith(GAMES_PREFIX)) {
                gameDirs.add(dir);
            } else if (name.startsWith(STATS_PREFIX)) {
                statsDirs.add(dir);
            }
        }

        // Match game dirs with stats dirs based on season identifier
        for (File gameDir : gameDirs) {
            String gameSeasonId = extractSeasonId(gameDir.getName());
            if (gameSeasonId == null) continue;

            for (File statsDir : statsDirs) {
                String statsSeasonId = extractSeasonId(statsDir.getName());
                if (gameSeasonId.equals(statsSeasonId)) {
                    pairs.add(new SeasonPair(gameDir, statsDir, gameSeasonId));
                    break;
                }
            }
        }

        return pairs;
    }

    private static String extractSeasonId(String dirName) {
        Matcher matcher = SEASON_PATTERN.matcher(dirName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void processSeasonPair(SeasonPair seasonPair, ObjectMapper mapper) throws Exception {
        File gamesDir = seasonPair.gamesDir;
        File statsDir = seasonPair.statsDir;
        String seasonId = seasonPair.seasonId;

        System.out.println("Processing season: " + seasonId);
        System.out.println("Games directory: " + gamesDir.getPath());
        System.out.println("Stats directory: " + statsDir.getPath());

        File[] gameFiles = gamesDir.listFiles((d, n) -> n.endsWith(".json"));
        if (gameFiles == null || gameFiles.length == 0) {
            System.err.println("No game JSON files found in " + gamesDir.getPath());
            return;
        }

        String outputFile = OUTPUT_DIR + "/games" + seasonId + ".ttl";
        int processedGames = 0;
        int skippedGames = 0;

        try (PrintWriter w = new PrintWriter(new FileWriter(outputFile))) {
            // prefixes
            w.println("@prefix rdf:       <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
            w.println("@prefix xsd:       <http://www.w3.org/2001/XMLSchema#> .");
            w.println("@prefix bball:     <http://www.ics.forth.gr/isl/Basketball#> .");
            w.println("@prefix ent:       <http://www.ics.forth.gr/isl/Basketball/entities/> .");
            w.println("@prefix euroleague: <https://www.euroleaguebasketball.net/euroleague/> .");
            w.println("@prefix foaf:      <http://xmlns.com/foaf/0.1/> .");
            w.println("@prefix rdfs:      <http://www.w3.org/2000/01/rdf-schema#> .");
            w.println("@prefix skos:      <https://www.w3.org/TR/skos-reference/> .");
            w.println();

            for (File gameFile : gameFiles) {
                JsonNode game = mapper.readTree(gameFile);
                String id = game.path("identifier").asText();
                if (id.isEmpty()) continue;

                // core game fields
                String gameCode = game.path("gameCode").asText();

                // Find corresponding stats file
                File statsFile = new File(statsDir, "stats_" + gameCode + ".json");
                if (!statsFile.exists()) {
                    System.err.println("Stats file not found for game " + gameCode + ". Skipping this game.");
                    skippedGames++;
                    continue;
                }

                JsonNode stats = mapper.readTree(statsFile);

                // Check if this game should be skipped
                if (shouldSkipGame(game, stats)) {
                    System.out.println("Skipping game " + gameCode + " (not played or no data)");
                    skippedGames++;
                    continue;
                }

                // Continue with normal processing for valid games
                String seasonAlias = game.path("season").path("alias").asText();
                String seasonCode  = game.path("season").path("code").asText();
                int    gameRound   = game.path("round").asInt();
                String phaseName   = game.path("phaseType").path("name").asText();
                String groupRaw    = game.path("group").path("rawName").asText().trim();
                String localDate   = game.path("localDate").asText();
                String venueCode   = game.path("venue").path("code").asText();

                String homeCode    = game.path("local").path("club").path("code").asText();
                String awayCode    = game.path("road").path("club").path("code").asText();
                int    homeScore   = game.path("local").path("score").asInt();
                int    awayScore   = game.path("road").path("score").asInt();
                String scorePair   = homeScore + "-" + awayScore;
                int    audience    = game.path("audience").asInt(0);
                JsonNode[] refs    = { game.path("referee1"), game.path("referee2"), game.path("referee3"), game.path("referee4") };

                // URIs
                String gameUri     = BASE_LEAGUE + "/euroleague/game-center/" + seasonAlias + "/-/" + seasonCode + "/" + gameCode;
                String leagueUri   = BASE_LEAGUE;
                String seasonUri   = "http://www.ics.forth.gr/isl/Basketball/entities/Season_" + seasonAlias.replace("-","_");
                String homeTeamUri = BASE_LEAGUE + "/euroleague/teams/-/" + homeCode;
                String awayTeamUri = BASE_LEAGUE + "/euroleague/teams/-/" + awayCode;
                // Game triples
                w.println("## Game");
                w.printf("<%s> rdf:type bball:Game ;%n", gameUri);
                w.printf("    bball:hasCode       \"%s\" ;%n", gameCode);
                w.printf("    rdfs:label          \"Game %s\" ;%n", gameCode);
                w.printf("    bball:hasLeague     <%s> ;%n", leagueUri);
                w.printf("    bball:hasSeason     <%s> ;%n", seasonUri);
                w.printf("    bball:hasPhase      \"%s\" ;%n", phaseName);
                w.printf("    bball:hasPhaseGroup \"%s\" ;%n", groupRaw);
                w.printf("    bball:hasRound      \"%d\"^^xsd:integer ;%n", gameRound);
                w.printf("    bball:hasDate       \"%s\"^^xsd:dateTime ;%n", localDate);
                w.printf("    bball:homeTeam      <%s> ;%n", homeTeamUri);
                w.printf("    bball:roadTeam      <%s> ;%n", awayTeamUri);
                w.printf("    bball:hasHomeTeamScore \"%d\"^^xsd:integer ;%n", homeScore);
                w.printf("    bball:hasRoadTeamScore \"%d\"^^xsd:integer ;%n", awayScore);
                w.printf("    bball:hasScore      \"%s\" ;%n", scorePair);
                if (audience > 0) w.printf("    bball:hasAudience   \"%d\"^^xsd:integer ;%n", audience);
                boolean hasOT    = game.path("local").path("partials").path("extraPeriods").size() > 0;

                w.printf("    bball:hasExtraTime  \"%b\"^^xsd:boolean ;%n", hasOT);
                w.printf("    bball:eventStarted  \"true\"^^xsd:boolean ;%n");
                w.printf("    bball:eventEnded    \"true\"^^xsd:boolean ;%n");
                for (JsonNode r : refs) {
                    String code = r.path("code").asText(null);
                    if (code != null && !code.isEmpty())
                        w.printf("    bball:hasReferee    ent:%s ;%n", code);
                }
                w.printf("    bball:gameVenue     ent:%s ;%n", venueCode);
                w.printf("    bball:hasTeamBoxscore <%s#boxscore_%s>,\n<%s#boxscore_%s> ;%n", gameUri, awayCode, gameUri, homeCode);

                String winUri = homeScore > awayScore ? homeTeamUri : awayTeamUri;
                String loseUri= homeScore > awayScore ? awayTeamUri : homeTeamUri;
                w.printf("    bball:winningTeam   <%s> ;%n", winUri);
                w.printf("    bball:losingTeam    <%s> .%n%n", loseUri);

                //  TeamBoxscore & Stats
                for (String teamCode : new String[]{awayCode, homeCode}) {
                    boolean isHome   = teamCode.equals(homeCode);
                    JsonNode gameTeam= game.path(isHome ? "local" : "road");
                    JsonNode statsTeam = stats.path(isHome ? "local" : "road");
                    String teamUri  = BASE_LEAGUE + "/euroleague/teams/-/" + teamCode;
                    String boxBase  = gameUri + "#boxscore_" + teamCode;

                    // TeamBoxscore
                    w.printf("## TeamBoxscore %s%n", teamCode);
                    w.printf("<%s> rdf:type bball:TeamBoxscore ;%n", boxBase);
                    w.printf("    bball:overTeam        <%s> ;%n", teamUri);
                    w.printf("    bball:hasTeamStatline <%s_Stats> ;%n", boxBase);

                    // head coach
                    String coach = statsTeam.path("coach").path("code").asText(null);
                    if (coach != null && !coach.isEmpty())
                        w.printf("    bball:hasHeadCoach    <%s/euroleague/players/-/%s> ;%n", BASE_LEAGUE, coach);

                    // individual participations
                    for (JsonNode p : statsTeam.path("players")) {
                        String pcode = p.path("player").path("person").path("code").asText();
                        w.printf("    bball:hasPlayerParticipation <%s_%s> ;%n", boxBase, pcode);
                    }
                    w.println("    .\n");

                    // WholeTeamStats
                    JsonNode T = statsTeam.path("total");
                    double   tmMin = T.path("timePlayed").asDouble() / 60.0;
                    int      tmVal = T.path("valuation").asInt();
                    int      pts   = T.path("points").asInt();
                    int      fg2m  = T.path("fieldGoalsMade2").asInt();
                    int      fg2a  = T.path("fieldGoalsAttempted2").asInt();
                    int      fg3m  = T.path("fieldGoalsMade3").asInt();
                    int      fg3a  = T.path("fieldGoalsAttempted3").asInt();
                    int      ftm   = T.path("freeThrowsMade").asInt();
                    int      fta   = T.path("freeThrowsAttempted").asInt();
                    int      totReb= T.path("totalRebounds").asInt();
                    int      dReb  = T.path("defensiveRebounds").asInt();
                    int      oReb  = T.path("offensiveRebounds").asInt();
                    int      ast   = T.path("assistances").asInt();
                    int      stl   = T.path("steals").asInt();
                    int      tov   = T.path("turnovers").asInt();
                    int      blkF  = T.path("blocksFavour").asInt();
                    int      blkA  = T.path("blocksAgainst").asInt();
                    int      fC    = T.path("foulsCommited").asInt();
                    int      fR    = T.path("foulsReceived").asInt();
                    int      pm    = T.path("plusMinus").asInt();

                    // shooting percentages
                    double pct2  = fg2a>0  ? 100.0*fg2m/fg2a : 0;
                    double pct3  = fg3a>0  ? 100.0*fg3m/fg3a : 0;
                    double pctFt = fta>0   ? 100.0*ftm/fta  : 0;
                    double pctFg = (fg2a+fg3a)>0 ? 100.0*(fg2m+fg3m)/(fg2a+fg3a) : 0;

                    // quarters
                    JsonNode Pp    = gameTeam.path("partials");
                    int q1 = Pp.path("partials1").asInt();
                    int q2 = Pp.path("partials2").asInt();
                    int q3 = Pp.path("partials3").asInt();
                    int q4 = Pp.path("partials4").asInt();
                    JsonNode extra    = Pp.path("extraPeriods");
                    List<Integer> otScores = new ArrayList<>();
                    for (int i = 1; ; i++) {
                        JsonNode node = extra.path(String.valueOf(i));
                        if (node.isMissingNode()) break;         // no more OT periods
                        otScores.add(node.asInt());
                    }
                    int e1 = q1;
                    int e2 = q1 + q2;
                    int e3 = q1 + q2 + q3;
                    int e4 = e3 + q4;

                    w.printf("## WholeTeamStats %s%n", teamCode);
                    w.printf("<%s_Stats> rdf:type bball:Statline ;%n", boxBase);
                    w.printf("    bball:minutesPlayed \"%s\"^^xsd:double ;%n", DF1.format(tmMin));
                    w.printf("    bball:PIR            \"%d\"^^xsd:integer ;%n", tmVal);
                    w.printf("    bball:points         \"%d\"^^xsd:integer ;%n", pts);
                    w.printf("    bball:fieldGoalsMade2\"%d\"^^xsd:integer ;%n", fg2m);
                    w.printf("    bball:fieldGoalsAttempted2\"%d\"^^xsd:integer ;%n", fg2a);
                    w.printf("    bball:fieldGoalsPer2 \"%s\"^^xsd:double ;%n", DF1.format(pct2));
                    w.printf("    bball:fieldGoalsMade3\"%d\"^^xsd:integer ;%n", fg3m);
                    w.printf("    bball:fieldGoalsAttempted3\"%d\"^^xsd:integer ;%n", fg3a);
                    w.printf("    bball:fieldGoalsPer3 \"%s\"^^xsd:double ;%n", DF1.format(pct3));
                    w.printf("    bball:freeThrowsMade \"%d\"^^xsd:integer ;%n", ftm);
                    w.printf("    bball:freeThrowsAttempted\"%d\"^^xsd:integer ;%n", fta);
                    w.printf("    bball:freeThrowsPer  \"%s\"^^xsd:double ;%n", DF1.format(pctFt));
                    w.printf("    bball:fieldGoalsMadeTotal\"%d\"^^xsd:integer ;%n", fg2m+fg3m);
                    w.printf("    bball:fieldGoalsAttemptedTotal\"%d\"^^xsd:integer ;%n", fg2a+fg3a);
                    w.printf("    bball:fieldGoalsPer  \"%s\"^^xsd:double ;%n", DF1.format(pctFg));
                    w.printf("    bball:totalRebounds  \"%d\"^^xsd:integer ;%n", totReb);
                    w.printf("    bball:defensiveRebounds\"%d\"^^xsd:integer ;%n", dReb);
                    w.printf("    bball:offensiveRebounds\"%d\"^^xsd:integer ;%n", oReb);
                    w.printf("    bball:quarter1points \"%d\"^^xsd:integer ;%n", q1);
                    w.printf("    bball:quarter2points \"%d\"^^xsd:integer ;%n", q2);
                    w.printf("    bball:quarter3points \"%d\"^^xsd:integer ;%n", q3);
                    w.printf("    bball:quarter4points \"%d\"^^xsd:integer ;%n", q4);
                    for (int i = 0; i < otScores.size(); i++) {

                        w.printf("    bball:extraTime%dPoints \"%d\"^^xsd:integer ;%n", i+1, otScores.get(i));
                    }
                    w.printf("    bball:endOfQuarter1points\"%d\"^^xsd:integer ;%n", e1);
                    w.printf("    bball:endOfQuarter2points\"%d\"^^xsd:integer ;%n", e2);
                    w.printf("    bball:endOfQuarter3points\"%d\"^^xsd:integer ;%n", e3);
                    w.printf("    bball:endOfQuarter4points\"%d\"^^xsd:integer ;%n", e4);
                    int score=e4;
                    for (int i = 0; i < otScores.size(); i++) {
                        score  += otScores.get(i);
                        w.printf("    bball:endOfExtraTime%dPoints \"%d\"^^xsd:integer ;%n", i+1, score);
                    }
                    w.printf("    bball:assists        \"%d\"^^xsd:integer ;%n", ast);
                    w.printf("    bball:steals         \"%d\"^^xsd:integer ;%n", stl);
                    w.printf("    bball:turnovers      \"%d\"^^xsd:integer ;%n", tov);
                    w.printf("    bball:blocks         \"%d\"^^xsd:integer ;%n", blkF);
                    w.printf("    bball:blocksAgainst \"%d\"^^xsd:integer ;%n", blkA);
                    w.printf("    bball:foulsCommitted \"%d\"^^xsd:integer ;%n", fC);
                    w.printf("    bball:foulsReceived  \"%d\"^^xsd:integer ;%n", fR);
                    w.printf("    bball:plusMinus      \"%d\"^^xsd:integer .%n%n", pm);

                    // Player‐by‐player
                    for (JsonNode entry : statsTeam.path("players")) {
                        JsonNode Ppnode = entry.path("player").path("person");
                        JsonNode S = entry.path("stats");
                        String pcode = Ppnode.path("code").asText();
                        String part  = boxBase + "_" + pcode;
                        boolean dnp   = S.path("timePlayed").asInt() == 0;
                        double pmin   = S.path("timePlayed").asDouble() / 60.0;

                        // Participation
                        w.printf("## PlayerBoxscore %s%n", pcode);
                        w.printf("<%s> rdf:type bball:PlayerParticipation ;%n", part);
                        w.printf("    bball:overPlayer      <%s/euroleague/players/-/%s> ;%n", BASE_LEAGUE, pcode);
                        w.printf("    bball:hasJerseyName   \"%s\" ;%n", Ppnode.path("jerseyName").asText());
                        w.printf("    bball:dnp             \"%b\"^^xsd:boolean ;%n", dnp);
                        w.printf("    bball:hasJerseyNumber \"%d\"^^xsd:integer ;%n", S.path("dorsal").asInt());
                        if (dnp) {
                            w.printf("    bball:hasPlayerStatline \"false\"^^xsd:boolean ;%n");
                        } else {
                            w.printf("    bball:hasPlayerStatline <%s_Stats> ;%n", part);
                        }
                        w.println("    .\n");

                        if (!dnp) {
                            // Stats
                            double p2m = S.path("fieldGoalsMade2").asInt();
                            double p2a = S.path("fieldGoalsAttempted2").asInt();
                            double p3m = S.path("fieldGoalsMade3").asInt();
                            double p3a = S.path("fieldGoalsAttempted3").asInt();
                            double pfm = S.path("freeThrowsMade").asInt();
                            double pfa = S.path("freeThrowsAttempted").asInt();
                            int    pv  = S.path("valuation").asInt();
                            int    ppts= S.path("points").asInt();
                            int    treb= S.path("totalRebounds").asInt();
                            int    pdReb= S.path("defensiveRebounds").asInt();
                            int    poReb= S.path("offensiveRebounds").asInt();
                            int    astp= S.path("assistances").asInt();
                            int    stlp= S.path("steals").asInt();
                            int    tovp= S.path("turnovers").asInt();
                            int    blkp= S.path("blocksFavour").asInt();
                            int    blka= S.path("blocksAgainst").asInt();
                            int    fCp = S.path("foulsCommited").asInt();
                            int    fRp = S.path("foulsReceived").asInt();
                            int    pmp = S.path("plusMinus").asInt();
                            boolean sf = S.path("startFive").asBoolean();

                            double pp2 = p2a>0 ? 100.0*p2m/p2a : 0;
                            double pp3 = p3a>0 ? 100.0*p3m/p3a : 0;
                            double ppf = pfa>0 ? 100.0*pfm/pfa : 0;
                            double pfg = (p2a+p3a)>0 ? 100.0*(p2m+p3m)/(p2a+p3a) : 0;

                            w.printf("## PlayerStats %s%n", pcode);
                            w.printf("<%s_Stats> rdf:type bball:Statline ;%n", part);
                            w.printf("    bball:minutesPlayed    \"%s\"^^xsd:double ;%n", DF1.format(pmin));
                            w.printf("    bball:PIR               \"%d\"^^xsd:integer ;%n", pv);
                            w.printf("    bball:points            \"%d\"^^xsd:integer ;%n", ppts);
                            w.printf("    bball:fieldGoalsMade2   \"%d\"^^xsd:integer ;%n", (int)p2m);
                            w.printf("    bball:fieldGoalsAttempted2\"%d\"^^xsd:integer ;%n", (int)p2a);
                            w.printf("    bball:fieldGoalsPer2    \"%s\"^^xsd:double ;%n", DF1.format(pp2));
                            w.printf("    bball:fieldGoalsMade3   \"%d\"^^xsd:integer ;%n", (int)p3m);
                            w.printf("    bball:fieldGoalsAttempted3\"%d\"^^xsd:integer ;%n", (int)p3a);
                            w.printf("    bball:fieldGoalsPer3    \"%s\"^^xsd:double ;%n", DF1.format(pp3));
                            w.printf("    bball:freeThrowsMade    \"%d\"^^xsd:integer ;%n", (int)pfm);
                            w.printf("    bball:freeThrowsAttempted\"%d\"^^xsd:integer ;%n", (int)pfa);
                            w.printf("    bball:freeThrowsPer     \"%s\"^^xsd:double ;%n", DF1.format(ppf));
                            w.printf("    bball:fieldGoalsMadeTotal\"%d\"^^xsd:integer ;%n", (int)(p2m+p3m));
                            w.printf("    bball:fieldGoalsAttemptedTotal\"%d\"^^xsd:integer ;%n", (int)(p2a+p3a));
                            w.printf("    bball:fieldGoalsPer     \"%s\"^^xsd:double ;%n", DF1.format(pfg));
                            w.printf("    bball:totalRebounds     \"%d\"^^xsd:integer ;%n", treb);
                            w.printf("    bball:defensiveRebounds\"%d\"^^xsd:integer ;%n", pdReb);
                            w.printf("    bball:offensiveRebounds\"%d\"^^xsd:integer ;%n", poReb);
                            w.printf("    bball:assists           \"%d\"^^xsd:integer ;%n", astp);
                            w.printf("    bball:steals            \"%d\"^^xsd:integer ;%n", stlp);
                            w.printf("    bball:turnovers         \"%d\"^^xsd:integer ;%n", tovp);
                            w.printf("    bball:blocks            \"%d\"^^xsd:integer ;%n", blkp);
                            w.printf("    bball:blocksAgainst     \"%d\"^^xsd:integer ;%n", blka);
                            w.printf("    bball:foulsCommitted     \"%d\"^^xsd:integer ;%n", fCp);
                            w.printf("    bball:foulsReceived     \"%d\"^^xsd:integer ;%n", fRp);
                            w.printf("    bball:plusMinus         \"%d\"^^xsd:integer ;%n", pmp);
                            w.printf("    bball:startingFive      \"%b\"^^xsd:boolean .%n%n", sf);
                        }
                    }
                }
                processedGames++;
            }

            System.out.println("RDF for season " + seasonId + " exported to " + outputFile);
            System.out.println("Processed games: " + processedGames);
            System.out.println("Skipped games: " + skippedGames);
        }
    }

    // Helper class to store a matched pair of game and stats directories
    private static class SeasonPair {
        final File gamesDir;
        final File statsDir;
        final String seasonId;

        public SeasonPair(File gamesDir, File statsDir, String seasonId) {
            this.gamesDir = gamesDir;
            this.statsDir = statsDir;
            this.seasonId = seasonId;
        }
    }
}