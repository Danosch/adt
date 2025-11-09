package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.ProductionCompany;

@ApplicationScoped
public class ProductionCompanyRepository extends BaseRepository {

	public Optional<ProductionCompany> findByTmdbId(int tmdbId) {
		return em.createQuery("select p from ProductionCompany p where p.tmdbId=:id", ProductionCompany.class)
				.setParameter("id", tmdbId).getResultStream().findFirst();
	}

	public ProductionCompany upsert(int tmdbId, String name, String originCountry) {
		ProductionCompany pc = findByTmdbId(tmdbId).orElseGet(ProductionCompany::new);
		pc.setTmdbId(tmdbId);
		pc.setName(name);
		pc.setOriginCountry(originCountry);
		return save(pc);
	}

	public ProductionCompany save(ProductionCompany pc) {
		if (pc.getId() == null) {
			em.persist(pc);
			return pc;
		}
		return em.merge(pc);
	}
}
