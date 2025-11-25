package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Genre;

/**
 * Repository für Genre-Entitäten mit Hilfsmethoden für TMDB-basierte Zugriffe.
 */
@ApplicationScoped
public class GenreRepository extends BaseRepository {

        /**
         * Sucht ein Genre anhand der TMDB-ID.
         */
        public Optional<Genre> findByTmdbId(int tmdbId) {
                return em.createQuery("select g from Genre g where g.tmdbId=:id", Genre.class)
                                .setParameter("id", tmdbId)
                                .getResultStream().findFirst();
        }

        /**
         * Legt ein Genre an oder aktualisiert dessen Namen.
         */
        public Genre upsert(int tmdbId, String name) {
                Genre g = findByTmdbId(tmdbId).orElseGet(Genre::new);
                g.setTmdbId(tmdbId);
                g.setName(name);
                return save(g);
        }

        /**
         * Persistiert oder merged die übergebene Entität abhängig vom Primärschlüssel.
         */
        public Genre save(Genre g) {
                if (g.getId() == null) {
                        em.persist(g);
                        return g;
                }
		return em.merge(g);
	}
}
