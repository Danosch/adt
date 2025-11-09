package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Genre;

@ApplicationScoped
public class GenreRepository extends BaseRepository {

	public Optional<Genre> findByTmdbId(int tmdbId) {
		return em.createQuery("select g from Genre g where g.tmdbId=:id", Genre.class)
				.setParameter("id", tmdbId)
				.getResultStream().findFirst();
	}

	public Genre upsert(int tmdbId, String name) {
		Genre g = findByTmdbId(tmdbId).orElseGet(Genre::new);
		g.setTmdbId(tmdbId);
		g.setName(name);
		return save(g);
	}

	public Genre save(Genre g) {
		if (g.getId() == null) {
			em.persist(g);
			return g;
		}
		return em.merge(g);
	}
}
