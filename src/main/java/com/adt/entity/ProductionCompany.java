package com.adt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "production_company")
@Getter
@Setter
public class ProductionCompany {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "tmdb_id", unique = true, nullable = false)
	private Integer tmdbId;

	@Column(nullable = false)
	private String name;

	@Column(name = "origin_country", length = 2)
	private String originCountry;
}
