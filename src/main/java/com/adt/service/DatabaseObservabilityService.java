package com.adt.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;

import com.adt.entity.dto.DatabaseObservabilitySnapshotDTO;
import com.adt.entity.dto.PoolMetricsDTO;
import com.adt.entity.dto.RuntimeMetricsDTO;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceMetrics;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Liefert kompakte Telemetrie zu Connection-Pool, Locks und Checkpoints und meldet sie als Micrometer-Metriken.
 */
@ApplicationScoped
public class DatabaseObservabilityService {

	private final AtomicInteger poolActive = new AtomicInteger();
	private final AtomicInteger poolAvailable = new AtomicInteger();
	private final AtomicInteger poolAwaiting = new AtomicInteger();
	private final AtomicInteger poolMax = new AtomicInteger();

	private final AtomicLong lockWaits = new AtomicLong();
	private final AtomicLong autovacuumWorkers = new AtomicLong();
	private final AtomicLong deadlocks = new AtomicLong();
	private final AtomicLong checkpointsTimed = new AtomicLong();
	private final AtomicLong checkpointsRequested = new AtomicLong();

	@Inject
	DataSource dataSource;

	@Inject
	MeterRegistry meterRegistry;

	@PostConstruct
	void registerGauges() {
		meterRegistry.gauge("adt.db.pool.active", poolActive);
		meterRegistry.gauge("adt.db.pool.available", poolAvailable);
		meterRegistry.gauge("adt.db.pool.awaiting", poolAwaiting);
		meterRegistry.gauge("adt.db.pool.max", poolMax);

		meterRegistry.gauge("adt.db.runtime.lock_waits", lockWaits);
		meterRegistry.gauge("adt.db.runtime.autovacuum", autovacuumWorkers);
		meterRegistry.gauge("adt.db.runtime.deadlocks_total", deadlocks);
		meterRegistry.gauge("adt.db.runtime.checkpoints.timed", checkpointsTimed);
		meterRegistry.gauge("adt.db.runtime.checkpoints.requested", checkpointsRequested);
	}

	public DatabaseObservabilitySnapshotDTO snapshot() {
		PoolMetricsDTO poolMetrics = collectPoolMetrics();
		RuntimeMetricsDTO runtimeMetrics = collectRuntimeMetrics();
		return new DatabaseObservabilitySnapshotDTO(poolMetrics, runtimeMetrics,
				"Aktuelle Pool- und Runtime-Metriken für Demo-Dashboards");
	}

	private PoolMetricsDTO collectPoolMetrics() {
		AgroalDataSource agroal = unwrapAgroal(dataSource);
		if (agroal == null) {
			return new PoolMetricsDTO(poolActive.get(), poolAvailable.get(), poolAwaiting.get(), poolMax.get());
		}

		AgroalDataSourceMetrics metrics = agroal.getMetrics();
		int active = metrics != null ? (int) metrics.activeCount() : 0;
		int available = metrics != null ? (int) metrics.availableCount() : 0;
		int awaiting = metrics != null ? (int) metrics.awaitingCount() : 0;
		int maxSize = agroal.getConfiguration().connectionPoolConfiguration().maxSize();

		poolActive.set(active);
		poolAvailable.set(available);
		poolAwaiting.set(awaiting);
		poolMax.set(maxSize);

		return new PoolMetricsDTO(active, available, awaiting, maxSize);
	}

	private RuntimeMetricsDTO collectRuntimeMetrics() {
		try (Connection connection = dataSource.getConnection()) {
			long lockWaitCount = queryLong(connection,
					"select count(*) from pg_stat_activity where wait_event_type = 'Lock'");
			long autovacuumCount = queryLong(connection,
					"select count(*) from pg_stat_activity where query ilike 'autovacuum:%'");
			long deadlockCount = queryLong(connection,
					"select coalesce(sum(deadlocks), 0) from pg_stat_database");

			try (PreparedStatement stmt = connection
					.prepareStatement("select checkpoints_timed, checkpoints_req from pg_stat_bgwriter")) {
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						checkpointsTimed.set(rs.getLong(1));
						checkpointsRequested.set(rs.getLong(2));
					}
				}
			}

			lockWaits.set(lockWaitCount);
			autovacuumWorkers.set(autovacuumCount);
			deadlocks.set(deadlockCount);

			return new RuntimeMetricsDTO(lockWaitCount, autovacuumCount, deadlockCount,
					checkpointsTimed.get(), checkpointsRequested.get());
		} catch (SQLException ex) {
			return new RuntimeMetricsDTO(lockWaits.get(), autovacuumWorkers.get(), deadlocks.get(),
					checkpointsTimed.get(), checkpointsRequested.get());
		}
	}

	private long queryLong(Connection connection, String sql) throws SQLException {
		try (PreparedStatement stmt = connection.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
			return rs.next() ? rs.getLong(1) : 0L;
		}
	}

	private AgroalDataSource unwrapAgroal(DataSource source) {
		if (source instanceof AgroalDataSource agroal) {
			return agroal;
		}
		try {
			return source.unwrap(AgroalDataSource.class);
		} catch (Exception ignored) {
			return null;
		}
	}
}
