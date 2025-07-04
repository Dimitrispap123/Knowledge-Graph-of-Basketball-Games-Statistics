# Knowledge Graph of Basketball Games Statistics

🏀 A comprehensive knowledge graph system for EuroLeague basketball statistics, transforming 25 seasons of data into a semantic web format with over 5 million RDF triples.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Installation](#installation)
- [Usage](#usage)
- [Data Structure](#data-structure)
- [SPARQL Examples](#sparql-examples)
- [Technologies Used](#technologies-used)
- [Project Structure](#project-structure)
- [Evaluation](#evaluation)
- [Contributing](#contributing)
- [License](#license)

## 🎯 Overview

This project creates a comprehensive knowledge graph for EuroLeague basketball statistics spanning 25 seasons (2000-2025). The system transforms raw basketball data from the EuroLeague API into structured RDF format using a custom-designed **BBall ontology**.

### Key Statistics
- **5.3M RDF triples** across 175 .ttl files (322 MB total)
- **6,283 games** analyzed
- **308.5K unique entities** including players, teams, coaches, referees
- **144,199 player participations** with detailed statistics
- **3,234 unique players** covered

## ✨ Features

### Data Processing
- **Automated data collection** from 9 EuroLeague API endpoints
- **Robust error handling** with retry logic and exponential backoff
- **Data validation** and cleaning procedures
- **Consistent URI policy** for unique entity identification

### Knowledge Graph
- **Custom BBall ontology** designed specifically for basketball data
- **Event-based architecture** with Game as the central entity
- **Comprehensive coverage** of all basketball entities (players, teams, coaches, referees, venues)
- **Statistical completeness** with 63 data properties and 25 object properties

### Query Capabilities
- **Full SPARQL support** for complex analytical queries
- **Multi-dimensional analysis** across players, teams, seasons, and games
- **Performance optimization** via Virtuoso SPARQL endpoint
- **Natural language integration** with 79% accuracy using GPT-4

## 🏗️ Architecture

The system follows a four-stage pipeline:

1. **Data Collection** → EuroLeague API integration
2. **Data Processing** → Cleaning and validation
3. **RDF Transformation** → BBall ontology mapping
4. **Knowledge Graph** → Virtuoso storage and SPARQL endpoint

```
EuroLeague API → JSON Processing → RDF Conversion → Virtuoso DB → SPARQL Endpoint
```

## 🚀 Installation

### Prerequisites
- Java 8+
- Maven 3.6+
- Virtuoso Universal Server v7
- 4GB+ RAM (recommended for large datasets)

### Setup
1. Clone the repository
```bash
git clone https://github.com/Dimitrispap123/Knowledge-Graph-of-Basketball-Games-Statistics.git
cd Knowledge-Graph-of-Basketball-Games-Statistics
```

2. Install dependencies
```bash
mvn clean install
```

3. Configure Virtuoso connection
```bash
# Edit src/main/resources/config.properties
virtuoso.host=localhost
virtuoso.port=1111
virtuoso.username=dba
virtuoso.password=dba
```

4. Start data collection
```bash
java -cp target/classes GetGamesStats
```

## 📊 Usage

### Data Collection
```java
// Collect data from EuroLeague API
GetGamesStats collector = new GetGamesStats();
collector.fetchAllSeasons();
```

### RDF Generation
```java
// Convert JSON data to RDF
GameToRDF converter = new GameToRDF();
converter.processAllGames();
```

### Data Loading
```java
// Load RDF files into Virtuoso
Virtuoso virtuoso = new Virtuoso();
virtuoso.uploadAllFiles();
```

### SPARQL Queries
```sparql
# Find top scorers with height < 1.85m
SELECT DISTINCT ?playerName ?points ?height
WHERE {
  ?game a bball:Game ;
        bball:hasTeamBoxscore ?boxscore .
  ?boxscore bball:hasPlayerParticipation ?participation .
  ?participation bball:overPlayer ?player ;
                bball:hasPlayerStatline ?statline .
  ?player rdfs:label ?playerName ;
          bball:hasHeight ?height .
  FILTER (?height < 1.85)
  ?statline bball:points ?points .
}
ORDER BY DESC(?points)
LIMIT 10
```

## 🗂️ Data Structure

### Core Classes
- **Game**: Central entity connecting all basketball data
- **Player**: Individual player information and statistics
- **Team**: Team data and performance metrics
- **Coach**: Coaching staff information
- **Referee**: Game officials data
- **Venue**: Game location details
- **Season**: Tournament organization
- **PlayerParticipation**: Player involvement in specific games
- **TeamBoxscore**: Team-level game statistics
- **Statline**: Detailed statistical measurements

### Statistical Coverage
- **Offensive Stats**: Points, field goals, 3-pointers, free throws
- **Defensive Stats**: Rebounds, steals, blocks
- **Playmaking**: Assists, turnovers
- **Advanced Metrics**: PIR (Performance Index Rating), plus/minus
- **Game Context**: Playing time, fouls, technical details

## 🔍 SPARQL Examples

### Coach Win Percentage
```sparql
SELECT (COUNT(?winningGame) / xsd:double(COUNT(?game)) AS ?winPercentage)
WHERE {
  ?coach a bball:Coach ;
