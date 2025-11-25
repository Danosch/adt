package com.adt.repository;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Gemeinsame Basis für Repository-Klassen mit Zugriff auf den EntityManager.
 */
public abstract class BaseRepository {
        @Inject
        protected EntityManager em;
}
