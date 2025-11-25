package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Movie;

/**
 * Repository für Movie-Entitäten.
 */
@ApplicationScoped
public class MovieRepository extends BaseRepository {

        /**
         * Sucht einen Film anhand der TMDB-ID.
         */
        public Optional<Movie> findByTmdbId(int tmdbId) {
                return em.createQuery("select m from Movie m where m.tmdbId=:id", Movie.class)
                                .setParameter("id", tmdbId).getResultStream().findFirst();
        }

        /**
         * Persistiert oder merged den Film je nach gesetztem Primärschlüssel.
         */
        public Movie save(Movie m) {
                if (m.getId() == null) {
                        em.persist(m);
			return m;
		}
		return em.merge(m);
	}
}
