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
 * Join-Tabelle zwischen Film und Genre.
 */
@Entity
@Table(name = "movie_genre", uniqueConstraints = @UniqueConstraint(name = "uq_movie_genre", columnNames = { "movie_id",
                "genre_id" }))
@Getter
@Setter
public class MovieGenre {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "genre_id", nullable = false)
	private Integer genreId;
}
