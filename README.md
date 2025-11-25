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

## Strukturhinweise
- Die Anwendung nutzt OkHttp für HTTP-Aufrufe und Jakarta EE (JAX-RS, JPA, CDI) im Rahmen von Quarkus.
- Ratenlimitierung der TMDB-API erfolgt über Header-Auswertung und Sleep-Mechanismus in `MovieImportService`.
- Alle wichtigen Klassen und Methoden sind mit Javadoc-Kommentaren versehen, die Zweck und Nutzung beschreiben.
