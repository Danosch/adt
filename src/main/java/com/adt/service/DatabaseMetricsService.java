package com.adt.service;

import java.time.Duration;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.adt.entity.Movie;
import com.adt.entity.dto.QueryPerformanceDTO;
import com.adt.repository.BaseRepository;

/**
 * Service, der gezielte Datenbankabfragen ausführt und deren Laufzeit als Micrometer-Metrik meldet.
 */
@ApplicationScoped
public class DatabaseMetricsService extends BaseRepository {

        @Inject
        MeterRegistry meterRegistry;

        /**
         * Führt eine index-basierte Suche über den Primärschlüssel aus.
         */
        @Timed(value = "adt.db.query.indexed", description = "Laufzeit einer über einen Index ausgeführten Abfrage")
        public QueryPerformanceDTO measureIndexedLookup(int movieId) {
                return runTimedQuery(
                                "indexed",
                                "adt.db.query.indexed",
                                "Primärschlüssel-Suche mit vorhandenem Index",
                                () -> em.createQuery("select m from Movie m where m.id=:id", Movie.class)
                                                .setParameter("id", movieId)
                                                .getResultList()
                                                .size());
        }

        /**
         * Führt eine Volltextsuche über Titel aus, die typischerweise keinen passenden Index besitzt.
         */
        @Timed(value = "adt.db.query.full-scan", description = "Laufzeit einer Abfrage ohne unterstützenden Index")
        public QueryPerformanceDTO measureUnindexedSearch(String titleFragment) {
                String term = titleFragment == null ? "" : titleFragment.toLowerCase();
                return runTimedQuery(
                                "full-scan",
                                "adt.db.query.full-scan",
                                "Titel-Scan ohne dedizierten Index",
                                () -> em.createQuery(
                                                "select m from Movie m where lower(m.title) like concat('%', :term, '%') order by m.releaseDate",
                                                Movie.class)
                                                .setParameter("term", term)
                                                .setMaxResults(200)
                                                .getResultList()
                                                .size());
        }

        private QueryPerformanceDTO runTimedQuery(
                        String queryType,
                        String timerName,
                        String description,
                        Supplier<Integer> queryRunner) {
                Timer.Sample sample = Timer.start(meterRegistry);
                int rows = queryRunner.get();
                long durationNanos = sample.stop(Timer.builder(timerName).description(description).register(meterRegistry));
                long durationMillis = Duration.ofNanos(durationNanos).toMillis();
                return new QueryPerformanceDTO(queryType, durationMillis, rows, description);
        }
}
