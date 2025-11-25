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
 * Join-Tabelle zwischen Film und Produktionsfirma.
 */
@Entity
@Table(name = "movie_production_company", uniqueConstraints = @UniqueConstraint(name = "uq_mpc_company", columnNames = {
                "movie_id", "production_company_id" }))
@Getter
@Setter
public class MovieProductionCompany {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "production_company_id", nullable = false)
	private Integer productionCompanyId;
}
