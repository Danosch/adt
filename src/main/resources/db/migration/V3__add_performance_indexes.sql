-- Indexe zur Beschleunigung der Performance-Metriken
CREATE INDEX idx_movie_release_date ON movie (release_date);
CREATE INDEX idx_movie_vote_avg_votes ON movie (vote_average DESC, vote_count DESC);
CREATE INDEX idx_movie_original_language_lower ON movie (lower(original_language));
CREATE INDEX idx_movie_release_popularity ON movie (release_date DESC, popularity DESC);