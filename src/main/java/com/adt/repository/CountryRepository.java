package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Country;

@ApplicationScoped
public class CountryRepository extends BaseRepository {

	public Optional<Country> findByIso(String iso) {
		return em.createQuery("select c from Country c where c.iso31661=:iso", Country.class)
				.setParameter("iso", iso).getResultStream().findFirst();
	}

	public Country upsert(String iso, String name) {
		Country c = findByIso(iso).orElseGet(Country::new);
		c.setIso31661(iso);
		if (name != null)
			c.setName(name);
		return save(c);
	}

	public Country save(Country c) {
		if (c.getId() == null) {
			em.persist(c);
			return c;
		}
		return em.merge(c);
	}
}
