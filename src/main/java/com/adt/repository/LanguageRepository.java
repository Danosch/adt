package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Language;

@ApplicationScoped
public class LanguageRepository extends BaseRepository {

	public Optional<Language> findByIso(String iso) {
		return em.createQuery("select l from Language l where l.iso6391=:iso", Language.class)
				.setParameter("iso", iso).getResultStream().findFirst();
	}

	public Language upsert(String iso, String englishName, String name) {
		Language l = findByIso(iso).orElseGet(Language::new);
		l.setIso6391(iso);
		if (englishName != null)
			l.setEnglishName(englishName);
		if (name != null)
			l.setName(name);
		return save(l);
	}

	public Language save(Language l) {
		if (l.getId() == null) {
			em.persist(l);
			return l;
		}
		return em.merge(l);
	}
}
