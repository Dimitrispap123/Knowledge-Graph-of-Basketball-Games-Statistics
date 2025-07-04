# Knowledge Graph of Basketball Games Statistics

 A comprehensive knowledge graph system for EuroLeague basketball statistics, transforming 25 seasons of data into a semantic web format with over 5 million RDF triples.

##  Overview

This project creates a comprehensive knowledge graph for EuroLeague basketball statistics spanning 25 seasons (2000-2025). The system transforms raw basketball data from the EuroLeague API into structured RDF format using a custom-designed **BBall ontology**.

### Key Statistics
- **5.3M RDF triples** across 175 .ttl files (322 MB total)
- **6,283 games** analyzed
- **308.5K unique entities** including players, teams, coaches, referees
- **144,199 player participations** with detailed statistics
- **3,234 unique players** covered

##  Features

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

##  Architecture

The system follows a four-stage pipeline:

1. **Data Collection** → EuroLeague API integration
2. **Data Processing** → Cleaning and validation
3. **RDF Transformation** → BBall ontology mapping
4. **Knowledge Graph** → Virtuoso storage and SPARQL endpoint

```
EuroLeague API → JSON Processing → RDF Conversion → Virtuoso DB → SPARQL Endpoint
```

##  Installation

### Prerequisites
- Java 8+
- Maven 3.6+
- Virtuoso Universal Server v7

## Data Structure

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
