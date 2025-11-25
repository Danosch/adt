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
 * Streaming- oder Kauf-Anbieter aus TMDB inklusive Region.
 */
@Entity
@Table(name = "watch_provider", uniqueConstraints = @UniqueConstraint(name = "uq_watch_provider_tmdb", columnNames = {
                "tmdb_id", "region" }))
@Getter
@Setter
public class WatchProvider {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "tmdb_id", nullable = false)
	private Integer tmdbId;

	@Column(nullable = false)
	private String name;

	@Column(name = "logo_path")
	private String logoPath;

	@Column(name = "display_priority")
	private Integer displayPriority;

	@Column(length = 2)
	private String region;
}
