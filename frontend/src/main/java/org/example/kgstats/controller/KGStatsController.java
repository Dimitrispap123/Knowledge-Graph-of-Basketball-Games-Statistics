package org.example.kgstats.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class KGStatsController {

    private static final String DB_URL = "jdbc:virtuoso://localhost:1111";
    private static final String DB_USERNAME = "dba";
    private static final String DB_PASSWORD = "dba";

    @GetMapping("/kgStatsFull")
    public Map<String, Object> getFullStats() {
        Map<String, Object> fullStats = new HashMap<>();

        try {
            Class.forName("virtuoso.jdbc3.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            // Basic stats - enhanced with new metrics
            Map<String, Integer> basic = new HashMap<>();
            basic.put("totalTriples", queryCount(conn, "SELECT COUNT(*) WHERE { ?s ?p ?o }"));
            basic.put("totalEntities", queryCount(conn, "SELECT COUNT(DISTINCT ?s) WHERE { ?s a [] }"));
            basic.put("totalPredicates", queryCount(conn, "SELECT COUNT(DISTINCT ?p) WHERE { ?s ?p ?o }"));
            basic.put("totalClasses", queryCount(conn, "SELECT COUNT(DISTINCT ?o) WHERE { ?s a ?o }"));
            basic.put("distinctSubjects", queryCount(conn, "SELECT COUNT(DISTINCT ?s) WHERE { ?s ?p ?o }"));
            basic.put("distinctObjects", queryCount(conn, "SELECT COUNT(DISTINCT ?o) WHERE { ?s ?p ?o FILTER(!isLiteral(?o)) }"));
            basic.put("triplesWithObjectURIs", queryCount(conn, "SELECT COUNT(*) WHERE { ?s ?p ?o FILTER(!isLiteral(?o)) }"));
            basic.put("triplesWithObjectLiterals", queryCount(conn, "SELECT COUNT(*) WHERE { ?s ?p ?o FILTER(isLiteral(?o)) }"));
            basic.put("distinctLiterals", queryCount(conn, "SELECT COUNT(DISTINCT ?o) WHERE { ?s ?p ?o FILTER(isLiteral(?o)) }"));

            fullStats.put("basic", basic);

            // Top Classes with enhanced data
            List<Map<String, Object>> classes = queryClassStats(conn);
            fullStats.put("classes", classes);

            // Top Properties
            List<Map<String, Object>> properties = queryPropertyStats(conn);
            fullStats.put("properties", properties);
            List<Map<String, Object>> propertySubjects = queryPropertySubjects(conn);
            fullStats.put("propertySubjects", propertySubjects);
            List<Map<String, Object>> propertyObjects = queryPropertyObjects(conn);
            fullStats.put("propertyObjects", propertyObjects);

            //Literal types distribution
            List<Map<String, Object>> literalTypes = queryLiteralTypes(conn);
            fullStats.put("literalTypes", literalTypes);

            //Top URI prefixes
            List<Map<String, Object>> uriPrefixes = queryURIPrefixes(conn);
            fullStats.put("uriPrefixes", uriPrefixes);

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fullStats;
    }

    private List<Map<String, Object>> queryClassStats(Connection conn) throws SQLException {
        List<Map<String, Object>> classes = new ArrayList<>();
        String sparql = """
            SELECT ?class (COUNT(?s) AS ?count) (COUNT(DISTINCT ?s) AS ?distinctInstances)
            WHERE { ?s a ?class } 
            GROUP BY ?class 
            ORDER BY DESC(?count) 
            LIMIT 20
            """;

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("class", rs.getString("class"));
            row.put("count", rs.getInt("count"));
            row.put("distinctInstances", rs.getInt("distinctInstances"));
            classes.add(row);
        }
        rs.close();
        stmt.close();
        return classes;
    }

    private List<Map<String, Object>> queryPropertyStats(Connection conn) throws SQLException {
        List<Map<String, Object>> properties = new ArrayList<>();
        String sparql = """
            SELECT ?p (COUNT(*) AS ?count) 
            WHERE { ?s ?p ?o } 
            GROUP BY ?p 
            ORDER BY DESC(?count) 
            LIMIT 20
            """;

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("property", rs.getString("p"));
            row.put("count", rs.getInt("count"));
            properties.add(row);
        }
        rs.close();
        stmt.close();
        return properties;
    }

    private List<Map<String, Object>> queryPropertySubjects(Connection conn) throws SQLException {
        List<Map<String, Object>> propertySubjects = new ArrayList<>();
        String sparql = """
            SELECT ?p (COUNT(DISTINCT ?s) AS ?count) 
            WHERE { ?s ?p ?o } 
            GROUP BY ?p 
            ORDER BY DESC(?count) 
            LIMIT 20
            """;

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("property", rs.getString("p"));
            row.put("count", rs.getInt("count"));
            propertySubjects.add(row);
        }
        rs.close();
        stmt.close();
        return propertySubjects;
    }

    private List<Map<String, Object>> queryPropertyObjects(Connection conn) throws SQLException {
        List<Map<String, Object>> propertyObjects = new ArrayList<>();
        String sparql = """
            SELECT ?p (COUNT(DISTINCT ?o) AS ?count) 
            WHERE { ?s ?p ?o } 
            GROUP BY ?p 
            ORDER BY DESC(?count) 
            LIMIT 20
            """;

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("property", rs.getString("p"));
            row.put("count", rs.getInt("count"));
            propertyObjects.add(row);
        }
        rs.close();
        stmt.close();
        return propertyObjects;
    }

    private List<Map<String, Object>> queryLiteralTypes(Connection conn) throws SQLException {
        List<Map<String, Object>> literalTypes = new ArrayList<>();
        String sparql = """
            SELECT (DATATYPE(?o) AS ?datatype) (COUNT(*) AS ?count) 
            WHERE { ?s ?p ?o FILTER(isLiteral(?o)) } 
            GROUP BY (DATATYPE(?o)) 
            ORDER BY DESC(?count) 
            LIMIT 15
            """;

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            String datatype = rs.getString("datatype");
            row.put("datatype", datatype != null ? datatype : "untyped");
            row.put("count", rs.getInt("count"));
            literalTypes.add(row);
        }
        rs.close();
        stmt.close();
        return literalTypes;
    }

    private List<Map<String, Object>> queryURIPrefixes(Connection conn) throws SQLException {
        List<Map<String, Object>> uriPrefixes = new ArrayList<>();
        String sparql = """
            SELECT (SUBSTR(?s, 1, CHARINDEX('#', ?s) - 1) AS ?prefix) (COUNT(*) AS ?count)
            WHERE { 
                ?s ?p ?o 
                FILTER(isURI(?s) && CONTAINS(STR(?s), '#'))
            } 
            GROUP BY (SUBSTR(?s, 1, CHARINDEX('#', ?s) - 1))
            ORDER BY DESC(?count) 
            LIMIT 15
            """;

        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("prefix", rs.getString("prefix"));
                row.put("count", rs.getInt("count"));
                uriPrefixes.add(row);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            // If the advanced query fails, try a simpler approach
            String simpleSparql = """
                SELECT ?s (COUNT(*) AS ?count)
                WHERE { ?s ?p ?o } 
                GROUP BY ?s 
                ORDER BY DESC(?count) 
                LIMIT 10
                """;

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SPARQL " + simpleSparql);
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                String uri = rs.getString("s");
                String prefix = extractPrefix(uri);
                row.put("prefix", prefix);
                row.put("count", rs.getInt("count"));
                uriPrefixes.add(row);
            }
            rs.close();
            stmt.close();
        }
        return uriPrefixes;
    }

    private String extractPrefix(String uri) {
        if (uri.contains("#")) {
            return uri.substring(0, uri.lastIndexOf("#"));
        } else if (uri.contains("/")) {
            return uri.substring(0, uri.lastIndexOf("/"));
        }
        return uri;
    }

    @GetMapping("/kgStats")
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();

        try {
            Class.forName("virtuoso.jdbc3.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            stats.put("totalTriples", queryCount(conn, "SELECT COUNT(*) WHERE { ?s ?p ?o }"));
            stats.put("distinctSubjects", queryCount(conn, "SELECT COUNT(DISTINCT ?s) WHERE { ?s ?p ?o }"));
            stats.put("distinctPredicates", queryCount(conn, "SELECT COUNT(DISTINCT ?p) WHERE { ?s ?p ?o }"));
            stats.put("distinctObjects", queryCount(conn, "SELECT COUNT(DISTINCT ?o) WHERE { ?s ?p ?o }"));

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return stats;
    }

    private int queryCount(Connection conn, String sparql) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SPARQL " + sparql);
        rs.next();
        int count = rs.getInt(1);
        rs.close();
        stmt.close();
        return count;
    }
}
