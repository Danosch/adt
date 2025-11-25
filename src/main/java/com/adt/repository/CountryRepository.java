package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Country;

/**
 * Repository für Länder-Stammdaten.
 */
@ApplicationScoped
public class CountryRepository extends BaseRepository {

        /**
         * Sucht ein Land anhand des ISO-3166-1-Codes.
         */
        public Optional<Country> findByIso(String iso) {
                return em.createQuery("select c from Country c where c.iso31661=:iso", Country.class)
                                .setParameter("iso", iso).getResultStream().findFirst();
        }

        /**
         * Legt ein Land an oder aktualisiert den Anzeigenamen.
         */
        public Country upsert(String iso, String name) {
                Country c = findByIso(iso).orElseGet(Country::new);
                c.setIso31661(iso);
                if (name != null)
                        c.setName(name);
                return save(c);
        }

        /**
         * Persistiert oder merged den Datensatz abhängig vom Primärschlüssel.
         */
        public Country save(Country c) {
                if (c.getId() == null) {
                        em.persist(c);
			return c;
		}
		return em.merge(c);
	}
}
