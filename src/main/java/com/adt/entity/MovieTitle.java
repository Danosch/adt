package com.adt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Alternative Titel eines Films je Region und Typ.
 */
@Entity
@Table(name = "movie_title")
@Getter
@Setter
public class MovieTitle {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "iso_3166_1", length = 2, nullable = false)
	private String iso31661;

	private String title;
	private String type;
}
