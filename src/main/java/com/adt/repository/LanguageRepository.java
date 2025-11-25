package com.adt.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import com.adt.entity.Language;

/**
 * Repository für Sprachstammdaten.
 */
@ApplicationScoped
public class LanguageRepository extends BaseRepository {

        /**
         * Sucht eine Sprache anhand des ISO-639-1-Codes.
         */
        public Optional<Language> findByIso(String iso) {
                return em.createQuery("select l from Language l where l.iso6391=:iso", Language.class)
                                .setParameter("iso", iso).getResultStream().findFirst();
        }

        /**
         * Legt eine Sprache an oder aktualisiert bekannte Felder.
         */
        public Language upsert(String iso, String englishName, String name) {
                Language l = findByIso(iso).orElseGet(Language::new);
                l.setIso6391(iso);
                if (englishName != null)
                        l.setEnglishName(englishName);
                if (name != null)
                        l.setName(name);
                return save(l);
        }

        /**
         * Persistiert oder merged die gegebene Sprache.
         */
        public Language save(Language l) {
                if (l.getId() == null) {
                        em.persist(l);
			return l;
		}
		return em.merge(l);
	}
}
