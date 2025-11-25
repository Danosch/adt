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
 * Zuordnung zwischen Filmen und Ländern (z. B. Produktionsland).
 */
@Entity
@Table(name = "movie_country", uniqueConstraints = @UniqueConstraint(name = "uq_movie_country", columnNames = {
                "movie_id", "iso_3166_1", "country_type_id" }))
@Getter
@Setter
public class MovieCountry {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "iso_3166_1", length = 2, nullable = false)
	private String iso31661;

	@Column(name = "country_type_id", nullable = false)
	private Integer countryTypeId;
}
