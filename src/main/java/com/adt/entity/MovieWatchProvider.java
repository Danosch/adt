package com.adt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * Verknüpft Filme mit Watch-Providern und deren Angebotstyp.
 */
@Entity
@Table(name = "movie_watch_provider", uniqueConstraints = @UniqueConstraint(name = "uq_movie_provider", columnNames = {
                "movie_id", "provider_id", "type" }))
@Getter
@Setter
public class MovieWatchProvider {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "provider_id", nullable = false)
	private Integer providerId;

	@Column(nullable = false)
	private String type;

	private String link;
}
