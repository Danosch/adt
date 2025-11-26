package com.adt.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import javax.sql.DataSource;

import okhttp3.HttpUrl;

/**
 * Verantwortlich für das Persistieren der aus TMDB geladenen Daten in der Datenbank.
 */
@ApplicationScoped
public class MoviePersistenceService {

	@Inject
	DataSource ds;

	@Inject
	TmdbClient tmdbClient;

	/**
	 * Synchronisiert die bekannte Genre-Liste mit TMDB und legt fehlende Einträge in der Datenbank an.
	 */
	public void refreshMovieGenres() {
		try {
			var url = Objects.requireNonNull(HttpUrl.parse("https://api.themoviedb.org/3/genre/movie/list"))
					.newBuilder()
					.addQueryParameter("language", "en-US")
					.build();

			JsonObject response = tmdbClient.getJson(url.toString());
			if (response == null || !response.containsKey("genres"))
				return;

			JsonArray genres = response.getJsonArray("genres");
			if (genres == null || genres.isEmpty())
				return;

			try (Connection c = ds.getConnection()) {
				for (JsonValue value : genres) {
					JsonObject genre = value.asJsonObject();
					upsertGenre(c, genre.getInt("id"), genre.getString("name", null));
				}
			}
		} catch (Exception e) {
			System.err.println("❌ Failed to refresh genre list: " + e.getMessage());
		}
	}

	/**
	 * Persistiert einen einzelnen Film inklusive aller abhängigen Datensätze.
	 */
	public boolean persistMovieImport(JsonObject json) throws Exception {
		JsonObject alternativeTitles = json.containsKey("alternative_titles")
				? json.getJsonObject("alternative_titles")
				: null;
		JsonObject credits = json.containsKey("credits") ? json.getJsonObject("credits") : null;
		JsonObject watchProviders = json.containsKey("watch/providers")
				? json.getJsonObject("watch/providers")
				: null;

		try (Connection c = ds.getConnection()) {
			c.setAutoCommit(false);
			try {
				if (json.containsKey("original_language")) {
					String iso = json.getString("original_language", null);
					if (iso != null)
						upsertLanguage(c, iso, null, null);
				}

				if (json.containsKey("genres")) {
					JsonArray arr = json.getJsonArray("genres");
					for (JsonValue v : arr) {
						JsonObject g = v.asJsonObject();
						upsertGenre(c, g.getInt("id"), g.getString("name", null));
					}
				}

				Long productionTypeId = upsertCountryType(c);
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

				Long moviePk = upsertMovie(c, json);
				clearMovieRelations(c, moviePk);

				linkMovieGenres(c, moviePk, json.getJsonArray("genres"));
				linkMovieSpokenLanguages(c, moviePk, json.getJsonArray("spoken_languages"));
				linkMovieCountries(c, moviePk, productionTypeId, json.getJsonArray("production_countries"));
				linkMovieProductionCompanies(c, moviePk, json.getJsonArray("production_companies"));

				replaceMovieTitles(c, moviePk, alternativeTitles);
				replaceMovieWatchProviders(c, moviePk, watchProviders);

				Map<Integer, Long> personCache = new HashMap<>();
				if (credits != null) {
					JsonArray cast = credits.getJsonArray("cast");
					if (cast != null)
						replaceMovieCast(c, moviePk, cast, personCache);
					JsonArray crew = credits.getJsonArray("crew");
					if (crew != null)
						replaceMovieCrew(c, moviePk, crew, personCache);
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

	private void upsertGenre(Connection c, int tmdbId, String name) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO genre (tmdb_id, name) VALUES (?, ?) "
						+ "ON CONFLICT (tmdb_id) DO UPDATE SET name = EXCLUDED.name")) {
			ps.setInt(1, tmdbId);
			ps.setString(2, name);
			ps.executeUpdate();
		}
	}

	private void upsertLanguage(Connection c, String iso639_1, String englishName, String name) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO language (iso_639_1, english_name, name) VALUES (?, ?, ?) "
						+ "ON CONFLICT (iso_639_1) DO UPDATE SET "
						+ "english_name = COALESCE(EXCLUDED.english_name, language.english_name), "
						+ "name = COALESCE(EXCLUDED.name, language.name)")) {
			ps.setString(1, iso639_1);
			ps.setString(2, englishName);
			ps.setString(3, name);
			ps.executeUpdate();
		}
	}

	private void upsertCountry(Connection c, String iso3166_1, String name) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO country (iso_3166_1, name) VALUES (?, ?) "
						+ "ON CONFLICT (iso_3166_1) DO UPDATE SET name = EXCLUDED.name")) {
			ps.setString(1, iso3166_1);
			ps.setString(2, name);
			ps.executeUpdate();
		}
	}

	private Long upsertCountryType(Connection c) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO country_type (code, description) VALUES (?, ?) "
						+ "ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description",
				Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, "production");
			ps.setString(2, "Production country");
			ps.executeUpdate();
		}
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM country_type WHERE code = ?")) {
			ps.setString(1, "production");
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
				"INSERT INTO production_company (tmdb_id, name, origin_country) VALUES (?, ?, ?) "
						+ "ON CONFLICT (tmdb_id) DO UPDATE SET "
						+ "name = COALESCE(EXCLUDED.name, production_company.name), "
						+ "origin_country = COALESCE(EXCLUDED.origin_country, production_company.origin_country)")) {
			ps.setInt(1, tmdbId);
			ps.setString(2, name);
			if (oc != null)
				ps.setString(3, oc);
			else
				ps.setNull(3, Types.VARCHAR);
			ps.executeUpdate();
		}
	}

	private Long upsertPerson(Connection c, JsonObject person) throws Exception {
		long tmdb = person.getInt("id");
		String name = person.getString("name", null);
		String gender = genderFromInt(person.getInt("gender", 0));
		String birthday = person.getString("birthday", null);
		String deathday = person.getString("deathday", null);
		String placeOfBirth = blankToNull(person.getString("place_of_birth", null));
		String profilePath = blankToNull(person.getString("profile_path", null));
		String imdbId = blankToNull(person.getString("imdb_id", null));
		String knownFor = blankToNull(person.getString("known_for_department", null));
		String homepage = blankToNull(person.getString("homepage", null));

		String insert = "INSERT INTO person (tmdb_id, name, gender, birthday, deathday, place_of_birth, "
				+ "profile_path, imdb_id, known_for_department, homepage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
				+ "ON CONFLICT (tmdb_id) DO UPDATE SET "
				+ "name = EXCLUDED.name, gender = EXCLUDED.gender, birthday = EXCLUDED.birthday, "
				+ "deathday = EXCLUDED.deathday, place_of_birth = EXCLUDED.place_of_birth, profile_path = EXCLUDED.profile_path, "
				+ "imdb_id = COALESCE(EXCLUDED.imdb_id, person.imdb_id), known_for_department = EXCLUDED.known_for_department, homepage = EXCLUDED.homepage";

		try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
			ps.setLong(1, tmdb);
			ps.setString(2, name);
			ps.setString(3, gender);
			setDate(ps, 4, birthday);
			setDate(ps, 5, deathday);
			ps.setString(6, placeOfBirth);
			ps.setString(7, profilePath);
			ps.setString(8, imdbId);
			ps.setString(9, knownFor);
			ps.setString(10, homepage);
			ps.executeUpdate();
		}

		return findIdByTmdb(c, "person", (int) tmdb);
	}

	private Long upsertMovie(Connection c, JsonObject json) throws SQLException {
                String insert = "INSERT INTO movie (tmdb_id, imdb_id, adult, budget, homepage, original_language, "
                                + "original_title, overview, popularity, release_date, revenue, runtime, status, tagline, title, "
                                + "video, vote_average, vote_count) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON CONFLICT (tmdb_id) DO UPDATE SET "
                                + "imdb_id = COALESCE(EXCLUDED.imdb_id, movie.imdb_id), "
                                + "adult = EXCLUDED.adult, budget = EXCLUDED.budget, homepage = EXCLUDED.homepage, original_language = EXCLUDED.original_language, "
                                + "original_title = EXCLUDED.original_title, overview = EXCLUDED.overview, popularity = EXCLUDED.popularity, release_date = EXCLUDED.release_date, "
                                + "revenue = EXCLUDED.revenue, runtime = EXCLUDED.runtime, status = EXCLUDED.status, tagline = EXCLUDED.tagline, title = EXCLUDED.title, "
                                + "video = EXCLUDED.video, vote_average = EXCLUDED.vote_average, vote_count = EXCLUDED.vote_count";

		try (PreparedStatement ps = c.prepareStatement(insert)) {
			ps.setLong(1, json.getInt("id"));
			ps.setString(2, blankToNull(json.getString("imdb_id", null)));
			ps.setBoolean(3, json.getBoolean("adult", false));
			ps.setBigDecimal(4, new BigDecimal(json.getInt("budget", 0)));
			ps.setString(5, blankToNull(json.getString("homepage", null)));
			ps.setString(6, json.getString("original_language", null));
			ps.setString(7, json.getString("original_title", null));
			ps.setString(8, json.getString("overview", null));
			ps.setBigDecimal(9, new BigDecimal(json.getJsonNumber("popularity").doubleValue()));
			setDate(ps, 10, blankToNull(json.getString("release_date", null)));
			ps.setBigDecimal(11, new BigDecimal(json.getJsonNumber("revenue").doubleValue()));
			if (json.isNull("runtime"))
				ps.setNull(12, Types.INTEGER);
			else
				ps.setInt(12, json.getInt("runtime"));
			ps.setString(13, json.getString("status", null));
                        ps.setString(14, json.getString("tagline", null));
                        ps.setString(15, json.getString("title", null));
                        ps.setBoolean(16, json.getBoolean("video", false));
                        ps.setBigDecimal(17, new BigDecimal(json.getJsonNumber("vote_average").doubleValue()));
                        ps.setInt(18, json.getInt("vote_count"));
                        ps.executeUpdate();
                }

		long moviePk;
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM movie WHERE tmdb_id = ?")) {
			ps.setLong(1, json.getInt("id"));
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				moviePk = rs.getLong(1);
			}
		}

		return moviePk;
	}

	private void clearMovieRelations(Connection c, Long moviePk) throws SQLException {
		clearMovieRelation(c, "movie_genre", moviePk);
		clearMovieRelation(c, "movie_language", moviePk);
		clearMovieRelation(c, "movie_country", moviePk);
		clearMovieRelation(c, "movie_production_company", moviePk);
		clearMovieRelation(c, "movie_cast", moviePk);
		clearMovieRelation(c, "movie_crew", moviePk);
	}

	private void clearMovieRelation(Connection c, String table, Long moviePk) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE movie_id = ?")) {
			ps.setLong(1, moviePk);
			ps.executeUpdate();
		}
	}

	private void linkMovieGenres(Connection c, Long movieId, JsonArray genres) throws SQLException {
		if (genres == null || genres.isEmpty())
			return;
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?) "
						+ "ON CONFLICT (movie_id, genre_id) DO NOTHING")) {
			for (JsonValue v : genres) {
				JsonObject g = v.asJsonObject();
				ps.setLong(1, movieId);
				ps.setLong(2, g.getInt("id"));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void linkMovieSpokenLanguages(Connection c, Long movieId, JsonArray languages) throws SQLException {
		if (languages == null || languages.isEmpty())
			return;
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_language (movie_id, language_id) VALUES (?, ?) "
						+ "ON CONFLICT (movie_id, language_id) DO NOTHING")) {
			for (JsonValue v : languages) {
				JsonObject l = v.asJsonObject();
				ps.setLong(1, movieId);
				ps.setString(2, l.getString("iso_639_1"));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void linkMovieCountries(Connection c, Long movieId, Long countryTypeId, JsonArray countries)
			throws SQLException {
		if (countryTypeId == null || countries == null || countries.isEmpty())
			return;
		Set<String> inserted = new HashSet<>();
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_country (movie_id, iso_3166_1, country_type_id) VALUES (?, ?, ?) "
						+ "ON CONFLICT (movie_id, iso_3166_1, country_type_id) DO NOTHING")) {
			for (JsonValue v : countries) {
				JsonObject pc = v.asJsonObject();
				String iso = normalizeIso2(pc.getString("iso_3166_1", null));
				if (iso == null || !inserted.add(iso))
					continue;
				ps.setLong(1, movieId);
				ps.setString(2, iso);
				ps.setLong(3, countryTypeId);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void linkMovieProductionCompanies(Connection c, Long movieId, JsonArray companies) throws SQLException {
		if (companies == null || companies.isEmpty())
			return;
		Set<Long> inserted = new HashSet<>();
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_production_company (movie_id, production_company_id) VALUES (?, ?) "
						+ "ON CONFLICT (movie_id, production_company_id) DO NOTHING")) {
			for (JsonValue v : companies) {
				JsonObject pc = v.asJsonObject();
				Long pcId = findIdByTmdb(c, "production_company", pc.getInt("id"));
				if (pcId == null || !inserted.add(pcId))
					continue;
				ps.setLong(1, movieId);
				ps.setLong(2, pcId);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void replaceMovieTitles(Connection c, Long movieId, JsonObject alternativeTitles) throws SQLException {
		clearMovieRelation(c, "movie_title", movieId);
		if (alternativeTitles == null || !alternativeTitles.containsKey("titles"))
			return;
		JsonArray titles = alternativeTitles.getJsonArray("titles");
		if (titles == null || titles.isEmpty())
			return;
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_title (movie_id, iso_3166_1, title, type) VALUES (?, ?, ?, ?)")) {
			for (JsonValue value : titles) {
				JsonObject t = value.asJsonObject();
				String iso = normalizeIso2(t.getString("iso_3166_1", null));
				if (iso == null)
					continue;
				upsertCountry(c, iso, iso);
				String title = blankToNull(t.getString("title", null));
				String type = blankToNull(t.getString("type", null));
				ps.setLong(1, movieId);
				ps.setString(2, iso);
				if (title != null)
					ps.setString(3, title);
				else
					ps.setNull(3, Types.VARCHAR);
				if (type != null)
					ps.setString(4, type);
				else
					ps.setNull(4, Types.VARCHAR);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void replaceMovieWatchProviders(Connection c, Long movieId, JsonObject watchProviders) throws Exception {
		clearMovieRelation(c, "movie_watch_provider", movieId);
		if (watchProviders == null || !watchProviders.containsKey("results"))
			return;
		JsonObject results = watchProviders.getJsonObject("results");
		if (results == null)
			return;
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO movie_watch_provider (movie_id, provider_id, type, link) VALUES (?, ?, ?, ?) "
						+ "ON CONFLICT (movie_id, provider_id, type) DO UPDATE SET link = EXCLUDED.link")) {
			for (String regionCode : results.keySet()) {
				String iso = normalizeIso2(regionCode);
				if (iso == null)
					continue;
				upsertCountry(c, iso, iso);
				JsonObject region = results.getJsonObject(regionCode);
				if (region == null)
					continue;
				String link = blankToNull(region.getString("link", null));
				processWatchProviderType(c, ps, movieId, iso, link, region, "flatrate");
				processWatchProviderType(c, ps, movieId, iso, link, region, "buy");
				processWatchProviderType(c, ps, movieId, iso, link, region, "rent");
				processWatchProviderType(c, ps, movieId, iso, link, region, "ads");
				processWatchProviderType(c, ps, movieId, iso, link, region, "free");
			}
			ps.executeBatch();
		}
	}

	private void processWatchProviderType(Connection c, PreparedStatement ps, Long movieId, String regionIso,
			String link, JsonObject region, String type) throws SQLException {
		JsonArray providers = region.getJsonArray(type);
		if (providers == null)
			return;
		Set<Long> inserted = new HashSet<>();
		for (JsonValue value : providers) {
			JsonObject provider = value.asJsonObject();
			Integer tmdbId = provider.getInt("provider_id", 0);
			if (inserted.contains(tmdbId.longValue()))
				continue;
			String providerName = provider.getString("provider_name", null);
			Long providerId = upsertWatchProvider(c, tmdbId, providerName, regionIso);
			linkMovieWatchProvider(ps, movieId, providerId, type, link);
			inserted.add(providerId);
		}
	}

	private void replaceMovieCast(Connection c, Long movieId, JsonArray cast, Map<Integer, Long> personCache)
			throws Exception {
		clearMovieRelation(c, "movie_cast", movieId);
		Set<Integer> added = new HashSet<>();
		String insert = "INSERT INTO movie_cast (movie_id, person_id, cast_id, character, credit_id, order_index) "
				+ "VALUES (?, ?, ?, ?, ?, ?)";

		try (PreparedStatement ps = c.prepareStatement(insert)) {
			for (JsonValue v : cast) {
				JsonObject castMember = v.asJsonObject();
				Integer tmdbId = castMember.getInt("id", 0);
				if (added.contains(tmdbId))
					continue;

				Long personId = getOrFetchPersonId(c, tmdbId, personCache);
				if (personId == null)
					continue;

				ps.setLong(1, movieId);
				ps.setLong(2, personId);
				ps.setInt(3, castMember.getInt("cast_id", 0));
				ps.setString(4, blankToNull(castMember.getString("character", null)));
				ps.setString(5, blankToNull(castMember.getString("credit_id", null)));
				ps.setInt(6, castMember.getInt("order", 0));
				ps.addBatch();
				added.add(tmdbId);
			}
			ps.executeBatch();
		}
	}

	private void replaceMovieCrew(Connection c, Long movieId, JsonArray crew, Map<Integer, Long> personCache)
			throws Exception {
		clearMovieRelation(c, "movie_crew", movieId);
		Set<String> added = new HashSet<>();
		String insert = "INSERT INTO movie_crew (movie_id, person_id, department, job, credit_id) "
				+ "VALUES (?, ?, ?, ?, ?)";

		try (PreparedStatement ps = c.prepareStatement(insert)) {
			for (JsonValue v : crew) {
				JsonObject crewMember = v.asJsonObject();
				Integer tmdbId = crewMember.getInt("id", 0);

				Long personId = getOrFetchPersonId(c, tmdbId, personCache);
				if (personId == null)
					continue;

				String dept = crewMember.getString("department", null);
				String job = crewMember.getString("job", null);
				String key = personId + ":" + dept + ":" + job;
				if (added.contains(key))
					continue;
				added.add(key);

				ps.setLong(1, movieId);
				ps.setLong(2, personId);
				ps.setString(3, blankToNull(dept));
				ps.setString(4, blankToNull(job));
				ps.setString(5, blankToNull(crewMember.getString("credit_id", null)));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private Long getOrFetchPersonId(Connection c, Integer tmdbId, Map<Integer, Long> cache) throws Exception {
		if (cache.containsKey(tmdbId))
			return cache.get(tmdbId);

		Long personId = findIdByTmdb(c, "person", tmdbId);
		if (personId == null) {
			JsonObject person = tmdbClient.fetchPersonDetails(tmdbId);
			if (person == null)
				return null;
			personId = upsertPerson(c, person);
		}
		cache.put(tmdbId, personId);
		return personId;
	}

	private Long upsertWatchProvider(Connection c, Integer tmdbId, String name, String iso3166_1)
			throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO watch_provider (tmdb_id, name) VALUES (?, ?) "
						+ "ON CONFLICT (tmdb_id) DO UPDATE SET name = EXCLUDED.name",
				Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, tmdbId);
			ps.setString(2, name);
			ps.executeUpdate();
		}

		Long providerId = findIdByTmdb(c, "watch_provider", tmdbId);

		upsertCountry(c, iso3166_1, iso3166_1);
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO watch_provider_country (watch_provider_id, iso_3166_1) VALUES (?, ?) "
						+ "ON CONFLICT (watch_provider_id, iso_3166_1) DO NOTHING")) {
			ps.setLong(1, providerId);
			ps.setString(2, iso3166_1);
			ps.executeUpdate();
		}

		return providerId;
	}

	private void linkMovieWatchProvider(PreparedStatement ps, Long movieId, Long providerId, String type, String link)
			throws SQLException {
		ps.setLong(1, movieId);
		ps.setLong(2, providerId);
		ps.setString(3, type);
		if (link != null)
			ps.setString(4, link);
		else
			ps.setNull(4, Types.VARCHAR);
		ps.addBatch();
	}

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

	private void setDate(PreparedStatement ps, int index, String date) throws SQLException {
		if (date == null)
			ps.setNull(index, Types.DATE);
		else
			ps.setDate(index, Date.valueOf(date));
	}

	private String genderFromInt(int genderCode) {
		return switch (genderCode) {
		case 1 -> "female";
		case 2 -> "male";
		case 3 -> "non-binary";
		default -> null;
		};
	}

	private String normalizeIso2(String iso) {
		if (iso == null)
			return null;
		if (iso.length() == 2)
			return iso.toUpperCase();
		if (iso.length() == 3)
			return iso.substring(0, 2).toUpperCase();
		return null;
	}

	private String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
