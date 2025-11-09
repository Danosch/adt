package com.adt.repository;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

public abstract class BaseRepository {
	@Inject
	protected EntityManager em;
}