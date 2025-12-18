# Wichtige Performance-Metriken des Postgres Exporter

Der [Postgres Exporter](https://github.com/prometheus-community/postgres_exporter) stellt zahlreiche Metriken bereit,
die Aufschluss über Performance- und Stabilitätsfragen geben. Die folgende Übersicht fasst häufig genutzte Kennzahlen
zusammen und erläutert, warum sie relevant sind.

## Basis- und Verfügbarkeitsmetriken

- **pg_up** – Zeigt an, ob der Exporter erfolgreich mit der Datenbank kommuniziert (0/1). Frühwarnsignal für Ausfälle.
- **pg_exporter_scrape_duration_seconds / pg_exporter_scrapes_total** – Dauer und Anzahl der Scrapes helfen,
  Scrape-Overhead oder Verbindungsprobleme zu erkennen.

## Workload & Throughput

- **pg_stat_database_xact_commit / pg_stat_database_xact_rollback** – Anzahl erfolgreicher bzw. zurückgerollter
  Transaktionen; zeigt Last und Fehlerrate.
- **pg_stat_database_tup_returned / tup_fetched / tup_inserted / tup_updated / tup_deleted** – Zeigt, wie viele Tupel
  gelesen bzw. geschrieben werden; erlaubt eine Einschätzung der Lese-/Schreiblast je Datenbank.
- **pg_stat_database_blocks_read_total / blks_hit_total** – Anzahl physischer Reads vs. Cache-Hits; Grundlage für
  Cache-Hit-Ratio-Berechnungen.

## Speicher- und Cache-Effizienz

- **Buffer Cache Hit Ratio (aus blks_hit_total und blks_read_total)** – Hohe Ratio zeigt effizienten Cache-Einsatz;
  niedrige Ratio deutet auf I/O-Engpässe.
- **pg_stat_bgwriter_buffers_alloc / buffers_backend / buffers_checkpoint / buffers_clean** – Verteilung der
  Buffer-Zuordnung zwischen Backend, Checkpoint und Cleaning-Prozessen hilft, RAM-Bedarf und Checkpoint-Strategie zu
  bewerten.
- **pg_stat_bgwriter_checkpoint_write_time / checkpoint_sync_time** – Dauer von Checkpoints; hohe Werte können
  Latenzspitzen verursachen.

## WAL & Replikation

- **pg_wal_current_lsn_bytes** – Fortschritt des WAL-Logs; nützlich, um Wachstum und Platzbedarf zu beobachten.
- **pg_stat_archiver_archived_wals_total / failed_wals_total** – Erfolgreiche vs. fehlgeschlagene Archivierungen;
  wichtig für PITR-Fähigkeiten.
- **pg_stat_wal_receiver_lag / pg_stat_replication_lag_bytes** – Verzögerung von Standby-Knoten; zentrale Metrik für
  Replikations-Gesundheit.
- **pg_stat_replication_sent_bytes / write_bytes / flush_bytes / replay_bytes** – Detaillierte Sicht auf
  Replikationsfortschritt pro Standby.

## Sperren & Konflikte

- **pg_locks_count** – Anzahl aktueller Locks; ein Anstieg kann auf Blockierungen hindeuten.
- **pg_stat_database_deadlocks_total** – Zählt Deadlocks; schon wenige Vorkommnisse sind ein Warnsignal.
- **pg_stat_database_conflicts_total** – Konflikte auf Replikas (z. B. aufgrund langer Queries); wichtig bei
  Hot-Standby.

## Wartung & Autovacuum

- **pg_stat_all_tables_vacuum_count / autovacuum_count** – Wie oft Tabellen manuell bzw. automatisch gesäubert werden;
  zeigt, ob Autovacuum greift.
- **pg_stat_all_tables_analyze_count / autoanalyze_count** – Frequenz der Statistik-Aktualisierung; wichtig für
  Query-Pläne.
- **pg_stat_progress_vacuum / pg_stat_progress_analyze** – Fortschritt laufender Wartungsjobs; nützlich bei langen
  Operationen.

## Speicherplatz & Bloat-Indikatoren

- **pg_table_size_bytes / pg_total_relation_size_bytes** – Größe einzelner Tabellen bzw. inklusive Indexe; wachsendes
  Verhältnis kann auf Bloat hindeuten.
- **pg_stat_all_indexes_idx_scan / idx_tup_read / idx_tup_fetch** – Nutzung von Indexen; geringe Nutzung großer Indexe
  weist auf mögliche Optimierungen hin.

## Latenz & Query-Statistiken

- **pg_stat_database_blk_read_time / blk_write_time** – Zeit für Block-Lese-/Schreiboperationen; direkte Indikatoren für
  I/O-Latenz.
- **pg_stat_statements_total_calls / total_exec_time / mean_exec_time** (falls Extension aktiviert) – Häufigkeit und
  Dauer von Queries; Grundlage für Top-N-Query-Analysen.
- **pg_stat_statements_rows / shared_blks_hit / shared_blks_read** – Detaillierte Ressourcennutzung pro Statement.

## Fehler & Stabilität

- **pg_settings_pending_restart** – Gibt an, ob Konfigurationsänderungen einen Neustart erfordern; nützlich für
  Change-Planung.
- **pg_stat_database_temp_bytes / temp_files** – Einsatz temporärer Dateien; hohe Werte deuten auf unzureichende
  Work_mem oder unpassende Pläne hin.

## Praxis-Hinweise

- Kombiniere mehrere Metriken (z. B. Cache-Hit-Ratio mit Block-Read-Time), um Engpässe zuverlässig zu identifizieren.
- Richte Alerts auf Trends ein (z. B. wachsender Replikations-Lag, sinkende Cache-Hit-Ratio), nicht nur auf harte
  Schwellenwerte.
- Nutze Labels wie `datname`, `instance` und `server` aus den Exporter-Metriken, um Problemzonen schnell zu isolieren.
