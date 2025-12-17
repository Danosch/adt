# DB-Performance: Präsentationsideen mit geringem Aufwand

Diese Übersicht fasst leichtgewichtige Maßnahmen zusammen, die sich gut in einer Präsentation zeigen lassen. Fokus: schnell einrichtbar, visuell anschaulich.

## Schnelle Metrik-Quellen
- **Connection-Pool-Auslastung (Micrometer/Agroal)**: aktive vs. verfügbare Verbindungen, Wartezeiten auf den Pool. Zeigt sofort, ob die App die DB überbucht.
- **Slow-Query-Log mit Schwellenwert**: z. B. `log_min_duration_statement = 500ms` aktivieren und wenige Beispielabfragen sammeln. Aus den Log-Zeilen lassen sich Heatmaps oder Top-N-Tabellen bauen.
- **pg_stat_statements Top-Listen**: `calls`, mittlere und 95./99. Perzentile, Block-Reads. Mit wenig Setup (Extension laden) erhältst du Ranglisten für Queries.
- **Lock-Waits & Deadlocks**: `pg_locks`/`pg_stat_activity` oder Micrometer-Gauges für blockierte Sessions. Hervorragend für ein Live-Demo („wer blockiert wen?“).

## Visuelle Demos, die schnell wirken
- **Plan-Vergleich**: EXPLAIN/ANALYZE für eine indexfreundliche vs. eine unindexierte Abfrage (z. B. `release_date`-Range vs. `year(release_date)`). Screenshots der Plan-Knoten (Index Scan vs. Seq Scan) sind selbsterklärend.
- **Latency-Percentiles**: Micrometer-Timer für schnelle und langsame Endpunkte (z. B. `/db/metrics/release-range` vs. `/db/metrics/random-sort`). In einem Grafana-Panel lassen sich 50./95./99. Perzentile nebeneinander legen.
- **Pool-Hitzekarte**: Zeitachse mit „in use“ vs. „available“ Connections. Gute Story: zeigen, wie ein limitierter Pool vor DB-Überlast schützt.
- **Lock-Blocks**: Kurzer Demo-Thread, der eine Zeile sperrt, plus zweiter Thread mit Timeout. Ein einfaches Balkendiagramm (Wartezeit in ms) macht das sofort sichtbar.

## Wenig Aufwand, klarer Lerneffekt
- **Parameter-Tuning per Toggle**: Vor/Nach-Vergleich mit einem Parameter (z. B. `work_mem` oder Pool-Größe). Zwei Balken „vorher/nachher“ für Latenz oder Durchsatz.
- **Autovacuum-Sichtbarkeit**: Laufende Autovacuum-Jobs aus `pg_stat_activity` anzeigen. Für die Präsentation reicht ein kurzer Screenshot mit Dauer und betroffenen Tabellen.
- **Checkpoints & WAL-Rate**: Prometheus-Exporter oder `pg_stat_bgwriter` für Checkpoint-Häufigkeit. Ein Linienchart mit Checkpoints pro Minute zeigt schnell, ob zu oft geflusht wird.

## Ablauf für eine Demo (Beispiel)
1. **Baseline**: Eine performante Query aufrufen (`/db/metrics/release-range`). Latenz und Pool-Auslastung zeigen.
2. **Langsame Query**: `/db/metrics/random-sort` oder führende Wildcard nutzen, Slow-Query-Log/Tracing-Spans erfassen.
3. **Lock-Simulation**: Eine Transaktion offen lassen, zweite blockieren lassen, Metrik für Lock-Waits hervorheben.
4. **Plan-Vergleich**: EXPLAIN-Screenshots der beiden Queries nebeneinander legen und erklären, warum Index-Nutzung gewinnt.

So erhältst du mit wenigen Einstellungen (Micrometer, pg_stat_statements, Slow-Query-Log) genug Material für eine anschauliche und datenbasierte Präsentation.
