package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.CountryType;

@ApplicationScoped
public class CountryTypeRepository extends BaseRepository {

	public Optional<CountryType> findByCode(String code) {
		return em.createQuery("select ct from CountryType ct where ct.code=:c", CountryType.class)
				.setParameter("c", code).getResultStream().findFirst();
	}

	public Integer upsert(String code, String desc) {
		CountryType ct = findByCode(code).orElseGet(CountryType::new);
		ct.setCode(code);
		if (desc != null)
			ct.setDescription(desc);
		ct = save(ct);
		return ct.getId();
	}

	public CountryType save(CountryType ct) {
		if (ct.getId() == null) {
			em.persist(ct);
			return ct;
		}
		return em.merge(ct);
	}
}
