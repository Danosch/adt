package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.ProductionCompany;

/**
 * Repository für Produktionsfirmen.
 */
@ApplicationScoped
public class ProductionCompanyRepository extends BaseRepository {

        /**
         * Sucht eine Firma anhand ihrer TMDB-ID.
         */
        public Optional<ProductionCompany> findByTmdbId(int tmdbId) {
                return em.createQuery("select p from ProductionCompany p where p.tmdbId=:id", ProductionCompany.class)
                                .setParameter("id", tmdbId).getResultStream().findFirst();
        }

        /**
         * Legt eine Firma an oder aktualisiert Name und Herkunftsland.
         */
        public ProductionCompany upsert(int tmdbId, String name, String originCountry) {
                ProductionCompany pc = findByTmdbId(tmdbId).orElseGet(ProductionCompany::new);
                pc.setTmdbId(tmdbId);
                pc.setName(name);
                pc.setOriginCountry(originCountry);
                return save(pc);
        }

        /**
         * Persistiert oder merged die übergebene Produktionsfirma.
         */
        public ProductionCompany save(ProductionCompany pc) {
                if (pc.getId() == null) {
                        em.persist(pc);
			return pc;
		}
		return em.merge(pc);
	}
}
