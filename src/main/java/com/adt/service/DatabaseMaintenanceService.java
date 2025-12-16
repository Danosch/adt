package com.adt.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import com.adt.entity.dto.ExplainPlanDTO;
import com.adt.entity.dto.MaintenanceActionDTO;
import com.adt.repository.BaseRepository;

/**
 * Service für gezielte Wartungs- und Diagnoseaktionen rund um die Datenbank.
 */
@ApplicationScoped
public class DatabaseMaintenanceService extends BaseRepository {

	/**
	 * Führt ein ANALYZE über die aktuelle Datenbank aus, um Statistiken für den Optimizer zu aktualisieren.
	 */
	@Transactional
	public MaintenanceActionDTO analyzeStatistics() {
		long start = System.nanoTime();
		em.createNativeQuery("ANALYZE").executeUpdate();
		long durationMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
		return new MaintenanceActionDTO("analyze", durationMillis,
				"ANALYZE executed to refresh planner statistics across all tables");
	}

	/**
	 * Liefert einen Explain-Plan für eine indexfreundliche Range-Abfrage.
	 */
	public ExplainPlanDTO explainReleaseRange(int startYear, int endYear, int limit) {
		int effectiveStartYear = Math.min(startYear, endYear);
		int effectiveEndYear = Math.max(startYear, endYear);
		int effectiveLimit = Math.max(1, limit);
		LocalDate startDate = LocalDate.of(effectiveStartYear, 1, 1);
		LocalDate endDate = LocalDate.of(effectiveEndYear, 12, 31);

		String sql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "
				+ "select m.id, m.release_date from movie m where m.release_date between :start and :end "
				+ "order by m.release_date limit " + effectiveLimit;

		long start = System.nanoTime();
		@SuppressWarnings("unchecked")
		List<String> plan = (List<String>) em.createNativeQuery(sql)
				.setParameter("start", startDate)
				.setParameter("end", endDate)
				.getResultList()
				.stream()
				.map(Object::toString)
				.collect(Collectors.toList());
		long durationMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
		return new ExplainPlanDTO("explain-release-range", durationMillis,
				"BETWEEN on release_date with index support", plan);
	}

	/**
	 * Liefert einen Explain-Plan für eine unperformante Jahres-Extraktion, um den Unterschied zu zeigen.
	 */
	public ExplainPlanDTO explainYearExtraction(int year, int limit) {
		int effectiveLimit = Math.max(1, limit);

		String sql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "
				+ "select m.id, m.release_date from movie m "
				+ "where extract(year from m.release_date) = :year order by m.release_date limit "
				+ effectiveLimit;

		long start = System.nanoTime();
		@SuppressWarnings("unchecked")
		List<String> plan = (List<String>) em.createNativeQuery(sql)
				.setParameter("year", Math.max(1900, year))
				.getResultList()
				.stream()
				.map(Object::toString)
				.collect(Collectors.toList());
		long durationMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
		return new ExplainPlanDTO("explain-year-extraction", durationMillis,
				"year(extract) on release_date (forces function call, bypasses index)", plan);
	}
}
