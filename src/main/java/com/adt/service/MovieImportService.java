package com.adt.service;

import static java.util.Calendar.DATE;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import javax.sql.DataSource;

import com.adt.entity.dto.ImportStatsDTO;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@ApplicationScoped
public class MovieImportService {

	@Inject
	DataSource ds;

	private final OkHttpClient http = new OkHttpClient();

	private static final long MIN_CALL_INTERVAL_NANOS = 1_000_000_000L / 50; // 50 calls per second
	private final Object rateLimitLock = new Object();
	private long lastApiCallTime = 0L;

	private void awaitRateLimit() {
		synchronized (rateLimitLock) {
			long now = System.nanoTime();
			long earliestNextCall = lastApiCallTime + MIN_CALL_INTERVAL_NANOS;
			if (earliestNextCall > now) {
				long waitNanos = earliestNextCall - now;
				long millis = waitNanos / 1_000_000L;
				int nanos = (int) (waitNanos % 1_000_000L);
				try {
					Thread.sleep(millis, nanos);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Interrupted while waiting for API rate limit", e);
				}
				now = System.nanoTime();
			}
			lastApiCallTime = Math.max(now, earliestNextCall);
		}
	}

	private String token() {
		String t = System.getenv("TMDB_API_TOKEN");
		if (t == null || t.isBlank()) {
			throw new IllegalStateException("TMDB_API_TOKEN not set");
		}
		return t;
	}

	// ============================================================
	// Hauptmethode (wird vom Resource-Endpoint aufgerufen)
	// ============================================================
	public ImportStatsDTO importMovieRangeWithStats(int startId, int endId) {
		if (endId < startId) {
			throw new IllegalArgumentException("Parameter 'endId' must be >= 'startId'");
		}

		int imported = 0;
		int failed = 0;
		long start = System.currentTimeMillis();

		for (int id = startId; id <= endId; id++) {
				try {
				if (importOne(id))
					imported++;
				else
					failed++;
			} catch (Exception e) {
				failed++;
				System.err.println("❌ Import failed for TMDB id " + id + ": " + e.getMessage());
			}
		}

		long duration = System.currentTimeMillis() - start;
		return new ImportStatsDTO(imported, failed, duration);
	}

	// ============================================================
	// Einzelimport eines Movies
	// ============================================================
	private boolean importOne(int tmdbId) throws Exception {
		Request req = new Request.Builder()
				.url("https://api.themoviedb.org/3/movie/" + tmdbId + "?language=en-US")
				.get()
				.addHeader("accept", "application/json")
				.addHeader("Authorization", "Bearer " + token())
				.build();

		awaitRateLimit();

		try (Response resp = http.newCall(req).execute()) {
			if (resp.code() == 404)
				return false;
			if (!resp.isSuccessful())
				throw new RuntimeException("HTTP " + resp.code());

			String body = resp.body().string();
			JsonObject json = Json.createReader(new java.io.StringReader(body)).readObject();

			try (Connection c = ds.getConnection()) {
				c.setAutoCommit(false);
					try {
					// Lookup-Tabellen auffrischen
					if (json.containsKey("original_language")) {
						String iso = json.getString("original_language", null);
						if (iso != null)
							upsertLanguage(c, iso, null, null);
					}

					// Genres
					if (json.containsKey("genres")) {
						JsonArray arr = json.getJsonArray("genres");
						for (JsonValue v : arr) {
							JsonObject g = v.asJsonObject();
							upsertGenre(c, g.getInt("id"), g.getString("name", null));
						}
					}

					// Länder
					Long productionTypeId = upsertCountryType(c, "production", "Production country");
					if (json.containsKey("production_countries")) {
						JsonArray arr = json.getJsonArray("production_countries");
						for (JsonValue v : arr) {
							JsonObject pc = v.asJsonObject();
                                                        String iso = normalizeIso2(pc.getString("iso_3166_1", null));
                                                        String name = pc.getString("name", null);
                                                        if (iso != null)
                                                                upsertCountry(c, iso, (name == null || name.isBlank()) ? iso : name);
						}
					}

					// Produktionsfirmen
					if (json.containsKey("production_companies")) {
						JsonArray arr = json.getJsonArray("production_companies");
						for (JsonValue v : arr) {
							JsonObject pc = v.asJsonObject();
							int pcTmdb = pc.getInt("id");
							String n = pc.getString("name", null);
                                                        String oc = blankToNull(pc.getString("origin_country", null));
                                                        upsertProductionCompany(c, pcTmdb, n, oc);
						}
					}

					// Sprachen
					if (json.containsKey("spoken_languages")) {
						JsonArray arr = json.getJsonArray("spoken_languages");
						for (JsonValue v : arr) {
							JsonObject l = v.asJsonObject();
							String iso = l.getString("iso_639_1", null);
							String en = l.getString("english_name", null);
							String name = l.getString("name", null);
							if (iso != null)
								upsertLanguage(c, iso, en, name);
						}
					}

					// Movie selbst einfügen / aktualisieren
					Long moviePk = upsertMovie(c, json);

					// Zuordnungen
					if (json.containsKey("genres")) {
						JsonArray arr = json.getJsonArray("genres");
						for (JsonValue v : arr) {
							JsonObject g = v.asJsonObject();
							Long genreId = findIdByTmdb(c, "genre", g.getInt("id"));
							linkMovieGenre(c, moviePk, genreId);
						}
					}

					if (json.containsKey("spoken_languages")) {
						JsonArray arr = json.getJsonArray("spoken_languages");
						for (JsonValue v : arr) {
							JsonObject l = v.asJsonObject();
							String iso = l.getString("iso_639_1", null);
							linkMovieSpokenLanguage(c, moviePk, iso);
						}
					}

					if (json.containsKey("production_countries")) {
						JsonArray arr = json.getJsonArray("production_countries");
						for (JsonValue v : arr) {
							JsonObject pc = v.asJsonObject();
                                                        String iso = normalizeIso2(pc.getString("iso_3166_1", null));
                                                        if (iso != null)
                                                                linkMovieCountry(c, moviePk, iso, productionTypeId);
						}
					}

					if (json.containsKey("production_companies")) {
						JsonArray arr = json.getJsonArray("production_companies");
						for (JsonValue v : arr) {
							JsonObject pc = v.asJsonObject();
							int id = pc.getInt("id");
							Long pcId = findIdByTmdb(c, "production_company", id);
							linkMovieProductionCompany(c, moviePk, pcId);
						}
					}

					c.commit();
					return true;
				} catch (Exception e) {
					c.rollback();
					throw e;
				} finally {
					c.setAutoCommit(true);
				}
			}
		}
	}

	// ============================================================
	// UPSERT-Helfer
	// ============================================================
	private void upsertGenre(Connection c, int tmdbId, String name) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO genre (tmdb_id, name) VALUES (?, ?) " +
						"ON CONFLICT (tmdb_id) DO UPDATE SET name = EXCLUDED.name")) {
			ps.setInt(1, tmdbId);
			ps.setString(2, name);
			ps.executeUpdate();
		}
	}

	private void upsertLanguage(Connection c, String iso639_1, String englishName, String name) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO language (iso_639_1, english_name, name) VALUES (?, ?, ?) " +
						"ON CONFLICT (iso_639_1) DO UPDATE SET " +
						"english_name = COALESCE(EXCLUDED.english_name, language.english_name), " +
						"name = COALESCE(EXCLUDED.name, language.name)")) {
			ps.setString(1, iso639_1);
			ps.setString(2, englishName);
			ps.setString(3, name);
			ps.executeUpdate();
		}
	}

	private void upsertCountry(Connection c, String iso3166_1, String name) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO country (iso_3166_1, name) VALUES (?, ?) " +
						"ON CONFLICT (iso_3166_1) DO UPDATE SET name = EXCLUDED.name")) {
			ps.setString(1, iso3166_1);
			ps.setString(2, name);
			ps.executeUpdate();
		}
	}

	private Long upsertCountryType(Connection c, String code, String desc) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO country_type (code, description) VALUES (?, ?) " +
						"ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description",
				Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, code);
			ps.setString(2, desc);
			ps.executeUpdate();
		}
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM country_type WHERE code = ?")) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	private void upsertProductionCompany(Connection c, int tmdbId, String name, String originCountry)
			throws SQLException {
		String oc = normalizeIso2(originCountry);

                if (oc != null) {
                        upsertCountry(c, oc, oc);
                }

                try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO production_company (tmdb_id, name, origin_country) VALUES (?, ?, ?) " +
                                                "ON CONFLICT (tmdb_id) DO UPDATE SET name = EXCLUDED.name, origin_country = EXCLUDED.origin_country")) {
                        ps.setInt(1, tmdbId);
                        ps.setString(2, name);
                        if (oc != null)
                                ps.setString(3, oc);
                        else
                                ps.setNull(3, Types.VARCHAR);
                        ps.executeUpdate();
                }
        }

	private static Date toSqlDate(String s) {
		if (s == null || s.isBlank())
			return null;
		// TMDB liefert yyyy-MM-dd, parse defensiv
		try {
			return Date.valueOf(java.time.LocalDate.parse(s));
		} catch (Exception e) {
			return null;
		}
	}

	private Long upsertMovie(Connection c, jakarta.json.JsonObject j) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie (tmdb_id, imdb_id, title, original_title, original_language, adult, video, status, "
						+
						"release_date, budget, revenue, runtime, homepage, overview, popularity, vote_average, vote_count, tagline) "
						+
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
						"ON CONFLICT (tmdb_id) DO UPDATE SET " +
						"imdb_id=EXCLUDED.imdb_id, title=EXCLUDED.title, original_title=EXCLUDED.original_title, " +
						"original_language=EXCLUDED.original_language, adult=EXCLUDED.adult, video=EXCLUDED.video, " +
						"status=EXCLUDED.status, release_date=EXCLUDED.release_date, budget=EXCLUDED.budget, " +
						"revenue=EXCLUDED.revenue, runtime=EXCLUDED.runtime, homepage=EXCLUDED.homepage, " +
						"overview=EXCLUDED.overview, popularity=EXCLUDED.popularity, vote_average=EXCLUDED.vote_average, "
						+
						"vote_count=EXCLUDED.vote_count, tagline=EXCLUDED.tagline RETURNING id")) {
			ps.setInt(1, j.getInt("id"));
			ps.setString(2, j.getString("imdb_id", null));
			ps.setString(3, j.getString("title", null));
			ps.setString(4, j.getString("original_title", null));
			ps.setString(5, j.getString("original_language", null));
			ps.setObject(6, j.getBoolean("adult", false));
			ps.setObject(7, j.getBoolean("video", false));
			ps.setString(8, j.getString("status", null));

			// <-- hier: DATE korrekt setzen
			Date rd = toSqlDate(j.getString("release_date", null));
			if (rd == null)
				ps.setNull(9, DATE);
			else
				ps.setDate(9, rd);

			ps.setObject(10, j.isNull("budget") ? null : j.getInt("budget"));
			ps.setObject(11, j.isNull("revenue") ? null : j.getJsonNumber("revenue").longValue());
			ps.setObject(12, j.isNull("runtime") ? null : j.getInt("runtime"));
			ps.setString(13, j.getString("homepage", null));
			ps.setString(14, j.getString("overview", null));
			ps.setObject(15,
					j.isNull("popularity") ? null : new java.math.BigDecimal(j.getJsonNumber("popularity").toString()));
			ps.setObject(16, j.isNull("vote_average") ? null
					: new java.math.BigDecimal(j.getJsonNumber("vote_average").toString()));
			ps.setObject(17, j.isNull("vote_count") ? null : j.getInt("vote_count"));
			ps.setString(18, j.getString("tagline", null));

			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	// ============================================================
	// Verknüpfungen (Relations)
	// ============================================================
	private void linkMovieGenre(Connection c, Long movieId, Long genreId) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?) " +
						"ON CONFLICT (movie_id, genre_id) DO NOTHING")) {
			ps.setLong(1, movieId);
			ps.setLong(2, genreId);
			ps.executeUpdate();
		}
	}

	private void linkMovieSpokenLanguage(Connection c, Long movieId, String iso639_1) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_spoken_language (movie_id, iso_639_1) VALUES (?, ?) " +
						"ON CONFLICT (movie_id, iso_639_1) DO NOTHING")) {
			ps.setLong(1, movieId);
			ps.setString(2, iso639_1);
			ps.executeUpdate();
		}
	}

	private void linkMovieCountry(Connection c, Long movieId, String iso3166_1, Long countryTypeId)
			throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_country (movie_id, iso_3166_1, country_type_id) VALUES (?, ?, ?) " +
						"ON CONFLICT (movie_id, iso_3166_1, country_type_id) DO NOTHING")) {
			ps.setLong(1, movieId);
			ps.setString(2, iso3166_1);
			ps.setLong(3, countryTypeId);
			ps.executeUpdate();
		}
	}

	private void linkMovieProductionCompany(Connection c, Long movieId, Long pcId) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_production_company (movie_id, production_company_id) VALUES (?, ?) " +
						"ON CONFLICT (movie_id, production_company_id) DO NOTHING")) {
			ps.setLong(1, movieId);
			ps.setLong(2, pcId);
			ps.executeUpdate();
		}
	}

        private static String normalizeIso2(String s) {
                if (s == null)
                        return null;
                String t = s.trim();
                return t.isEmpty() ? null : t;
        }

        private static String blankToNull(String s) {
                if (s == null)
                        return null;
                return s.isBlank() ? null : s;
        }

	// Hilfsmethode zum Nachschlagen einer ID anhand tmdb_id
	private Long findIdByTmdb(Connection c, String table, int tmdbId) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + table + " WHERE tmdb_id = ?")) {
			ps.setInt(1, tmdbId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next())
					return rs.getLong(1);
				else
					return null;
			}
		}
	}
}
