package com.adt.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Repräsentiert eine Person (Cast/Crew) inklusive biografischer Basisdaten.
 */
@Entity
@Table(name = "person")
@Getter
@Setter
public class Person {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "tmdb_id", unique = true, nullable = false)
	private Integer tmdbId;

	@Column(name = "imdb_id", unique = true)
	private String imdbId;

	@Column(nullable = false)
	private String name;

	private Integer gender;

	@Column(name = "known_for_department")
	private Integer knownForDepartment;

	@Column(columnDefinition = "TEXT")
	private String biography;

	private LocalDate birthday;
	private LocalDate deathday;

	@Column(name = "place_of_birth")
	private String placeOfBirth;

	private String homepage;
	private Boolean adult;

	@Column(precision = 10, scale = 3)
	private BigDecimal popularity;
}
