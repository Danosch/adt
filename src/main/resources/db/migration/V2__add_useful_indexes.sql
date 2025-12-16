CREATE INDEX idx_person_alias_person_id ON person_alias (person_id);

CREATE INDEX idx_movie_title_movie_id ON movie_title (movie_id);
CREATE INDEX idx_movie_title_iso_3166_1 ON movie_title (iso_3166_1);

CREATE INDEX idx_movie_genre_movie_id ON movie_genre (movie_id);
CREATE INDEX idx_movie_genre_genre_id ON movie_genre (genre_id);

CREATE INDEX idx_movie_spoken_language_movie_id ON movie_spoken_language (movie_id);
CREATE INDEX idx_movie_spoken_language_iso_639_1 ON movie_spoken_language (iso_639_1);

CREATE INDEX idx_movie_country_movie_id ON movie_country (movie_id);
CREATE INDEX idx_movie_country_iso_3166_1 ON movie_country (iso_3166_1);
CREATE INDEX idx_movie_country_country_type_id ON movie_country (country_type_id);

CREATE INDEX idx_movie_production_company_movie_id ON movie_production_company (movie_id);
CREATE INDEX idx_movie_production_company_company_id ON movie_production_company (production_company_id);

CREATE INDEX idx_movie_cast_movie_id ON movie_cast (movie_id);
CREATE INDEX idx_movie_cast_person_id ON movie_cast (person_id);

CREATE INDEX idx_movie_crew_movie_id ON movie_crew (movie_id);
CREATE INDEX idx_movie_crew_person_id ON movie_crew (person_id);
CREATE INDEX idx_movie_crew_job_id ON movie_crew (job_id);

CREATE INDEX idx_movie_watch_provider_movie_id ON movie_watch_provider (movie_id);
CREATE INDEX idx_movie_watch_provider_provider_id ON movie_watch_provider (provider_id);

-- Indexe zur Beschleunigung der Performance-Metriken
CREATE INDEX idx_movie_release_date ON movie (release_date);
CREATE INDEX idx_movie_vote_avg_votes ON movie (vote_average DESC, vote_count DESC);
CREATE INDEX idx_movie_original_language_lower ON movie (lower(original_language));
CREATE INDEX idx_movie_release_popularity ON movie (release_date DESC, popularity DESC);