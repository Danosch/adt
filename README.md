# ADT Film-Import-Service

Diese Quarkus-Anwendung importiert Filmdaten aus der TMDB-API (The Movie Database) in eine relationale Datenbank. Sie stellt dafür REST-Endpunkte bereit und pflegt umfangreiche Stammdaten wie Genres, Sprachen, Länder, Produktionsfirmen, Besetzung und Streaming-Anbieter.

## Voraussetzungen
- Java 17+
- Docker (optional für DB/Flyway)
- Umgebungsvariable `TMDB_API_TOKEN` mit einem gültigen TMDB-Bearer-Token

## Wichtige Endpunkte
| Methode | Pfad | Beschreibung |
| --- | --- | --- |
| `POST` | `/import/movies?start={id}&end={id}` | Importiert Filme anhand eines TMDB-ID-Bereichs. |
| `POST` | `/import/movies/years?startYear={jahr}&endYear={jahr}` | Importiert alle Filme innerhalb eines Veröffentlichungsjahres-Bereichs. |
| `GET` | `/db/metrics/indexed?id={id}` | Misst eine indexgestützte Primärschlüsselabfrage. |
| `GET` | `/db/metrics/full-scan?term={titel}` | Misst eine unindexierte Titelsuche. |
| `GET` | `/db/metrics/year-extraction?year={jahr}` | Misst eine nicht indexfreundliche `year()`-Extraktion auf `release_date`. |
| `GET` | `/db/metrics/release-range?startYear={jahr}&endYear={jahr}&limit={n}` | Misst eine indexfreundliche BETWEEN-Abfrage über `release_date`. |
| `GET` | `/db/metrics/overview-scan?term={text}` | Misst einen Text-Scan über `overview` ohne Volltextindex. |
| `GET` | `/db/metrics/top-rated?minVotes={anzahl}&limit={n}` | Misst eine performante Sortierung nach `vote_average`/`vote_count`. |
| `GET` | `/db/metrics/random-sort?limit={n}` | Erzwingt eine vollständige Sortierung via `order by random()` (unperformant). |
| `GET` | `/db/metrics/wildcard-original-title?term={text}&limit={n}` | Führende Wildcard-Suche auf `original_title` (umgeht Index). |
| `GET` | `/db/metrics/language-filter?language={iso}&limit={n}` | Indexfreundliche Filterung nach `original_language`. |
| `GET` | `/db/metrics/recent-popular?startYear={jahr}&limit={n}` | Kombinierte Filterung auf `release_date` und Popularität (performant). |
| `GET` | `/db/metrics/load-test?iterations={anzahl}&limit={n}` | Führt mehrere Queries sequenziell aus, um spürbare DB-Last zu erzeugen. |

Die Endpunkte liefern jeweils ein DTO mit Importstatistiken (erfolgreiche/fehlgeschlagene Importe und Dauer in Millisekunden).

## Kernkomponenten
- **`MovieImportResource` / `MovieImportResourceImpl`**: REST-Schnittstelle, die Requests validiert und den Import-Service aufruft.
- **`MovieImportService`**: Enthält die komplette Logik für API-Aufrufe, Ratenlimitierung, Parsing und Persistierung.
- **Entitäten (`src/main/java/com/adt/entity/*`)**: JPA-Modelle für Filme, Personen, Genres, Länder, Produktionsfirmen, Watch-Provider u. a. sowie die jeweiligen Join-Tabellen.
- **Repositories (`src/main/java/com/adt/repository/*`)**: Schlanke Datenzugriffsschicht mit Hilfsmethoden zum Suchen und Upserten nach TMDB-IDs oder ISO-Codes.
- **DTOs (`src/main/java/com/adt/entity/dto/*`)**: Transportobjekte für Importstatistiken und API-Antworten.

## Ablauf eines Imports
1. **Validierung & Normalisierung**: Die Resource-Schicht prüft Parameter (ID- oder Jahresbereiche) und passt sie an zulässige Grenzen an.
2. **Rate-Limit & Stammdaten**: Der Service synchronisiert das API-Rate-Limit sowie die Genre-Liste.
3. **API-Aufruf(e)**: Der Service ruft TMDB (Movie-Details oder Discover) mit Token-Authentifizierung auf.
4. **Persistierung**: Alle relevanten Entitäten werden per Upsert angelegt/aktualisiert. Relationen (Genres, Sprachen, Länder, Produktion, Cast/Crew, Watch-Provider, Alternativtitel) werden vor dem Einfügen bereinigt.
5. **Statistiken**: Nach Abschluss wird die Anzahl importierter/fehlgeschlagener Datensätze sowie die Dauer zurückgegeben.

## Entwicklung & Betrieb
- **Dev-Mode starten**: `./mvnw quarkus:dev`
- **Build**: `./mvnw package`
- **Native Build** (optional): `./mvnw package -Dnative`

Die Datenbanktabellen werden über Flyway-Migrationen bereitgestellt (`/flyway`-Ordner). Für containerisierte Läufe steht `docker-compose.yaml` zur Verfügung.

## Hinweise zur DB-Performance
- Die neuen Endpunkte liefern Micrometer-Metriken (Prometheus) für performante und unperformante Abfragen und helfen bei Vergleichsmessungen. Über `/db/metrics/load-test` lässt sich zudem kurzzeitig Last erzeugen.
- Zusätzliche Indexe (Migration `V2__add_useful_indexes.sql`) decken nun auch `movie.release_date`, die kombinierte Sortierung `vote_average/vote_count`, `lower(original_language)` sowie `(release_date, popularity)` ab und beschleunigen typische Filter- und Sortierabfragen.
- Weitere sinnvolle Maßnahmen:
  - Query-Parameter mit sinnvollen Limits versehen, um Resultsets klein zu halten.
  - Vermeiden von Funktionen auf indizierten Spalten (z. B. `lower(column)`, `year(column)`), wenn stattdessen Range-Abfragen möglich sind.
  - Regelmäßig `EXPLAIN (ANALYZE, BUFFERS)` nutzen, um Indexnutzung und I/O zu prüfen.
  - Statistiken aktuell halten (`ANALYZE`) und Autovacuum-Parameter auf die Datenlast abstimmen.

## DB-Last mit den Metrik-Endpunkten erzeugen
1. Anwendung starten (z. B. `./mvnw quarkus:dev`) und sicherstellen, dass Prometheus/ Micrometer aktiviert ist.
2. Einen Ausgangswert messen, z. B. eine performante Range-Abfrage:
   ```bash
   curl "http://localhost:8080/db/metrics/release-range?startYear=2010&endYear=2020&limit=200"
   ```
3. Anschließend unperformante Pfade aufrufen, um Last aufzubauen (führen häufige `order by random()` oder führende Wildcards aus):
   ```bash
   curl "http://localhost:8080/db/metrics/random-sort?limit=500"
   curl "http://localhost:8080/db/metrics/wildcard-original-title?term=man&limit=500"
   ```
4. Mit `/db/metrics/load-test` gezielt mehrere langsame und schnelle Abfragen hintereinander triggern. Die Parameter `iterations` und `limit` steuern dabei Lastdauer und Resultset-Größe:
   ```bash
   curl "http://localhost:8080/db/metrics/load-test?iterations=5&limit=250"
   ```
5. Prometheus-Scrapes oder `quarkus:dev`-Logausgaben liefern die Messwerte (Micrometer-Timer). Unter Last sollten sich die Unterschiede zwischen Index-gestützten und Voll-Scan-Abfragen deutlich zeigen.

## Strukturhinweise
- Die Anwendung nutzt OkHttp für HTTP-Aufrufe und Jakarta EE (JAX-RS, JPA, CDI) im Rahmen von Quarkus.
- Ratenlimitierung der TMDB-API erfolgt über Header-Auswertung und Sleep-Mechanismus in `MovieImportService`.
- Alle wichtigen Klassen und Methoden sind mit Javadoc-Kommentaren versehen, die Zweck und Nutzung beschreiben.
