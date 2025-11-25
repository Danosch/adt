package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.CountryType;

/**
 * Repository für CountryType-Einträge zur Verwaltung von Länderbeziehungsarten.
 */
@ApplicationScoped
public class CountryTypeRepository extends BaseRepository {

        /**
         * Sucht einen CountryType anhand seines Codes.
         */
        public Optional<CountryType> findByCode(String code) {
                return em.createQuery("select ct from CountryType ct where ct.code=:c", CountryType.class)
                                .setParameter("c", code).getResultStream().findFirst();
        }

        /**
         * Legt einen CountryType an oder aktualisiert die Beschreibung.
         */
        public Integer upsert(String code, String desc) {
                CountryType ct = findByCode(code).orElseGet(CountryType::new);
                ct.setCode(code);
                if (desc != null)
                        ct.setDescription(desc);
                ct = save(ct);
                return ct.getId();
        }

        /**
         * Persistiert oder merged den übergebenen CountryType.
         */
        public CountryType save(CountryType ct) {
                if (ct.getId() == null) {
                        em.persist(ct);
			return ct;
		}
		return em.merge(ct);
	}
}
