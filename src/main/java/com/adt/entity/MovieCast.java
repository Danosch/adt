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
 * Cast-Zuordnung inklusive gespieltem Charakter.
 */
@Entity
@Table(name = "movie_cast", uniqueConstraints = @UniqueConstraint(name = "uq_movie_cast", columnNames = { "movie_id",
                "person_id", "character_name" }))
@Getter
@Setter
public class MovieCast {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "person_id", nullable = false)
	private Integer personId;

	@Column(name = "character_name")
	private String characterName;

	@Column(name = "cast_order")
	private Integer castOrder;
}
