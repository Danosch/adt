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
 * JPA-Entity für die movie-Tabelle mit allen Stammdaten eines Films aus TMDB.
 */
@Entity
@Table(name = "movie")
@Getter
@Setter
public class Movie {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "tmdb_id", unique = true, nullable = false)
	private Integer tmdbId;

	@Column(name = "imdb_id", unique = true)
	private String imdbId;

	private String title;

	@Column(name = "original_title")
	private String originalTitle;

	@Column(name = "original_language", length = 2)
	private String originalLanguage;

	private Boolean adult;
	private Boolean video;
	private String status;

	@Column(name = "release_date")
	private LocalDate releaseDate;

	private Integer budget;
	private Long revenue;
	private Integer runtime;
	private String homepage;

	@Column(columnDefinition = "TEXT")
	private String overview;

	@Column(precision = 12, scale = 3)
	private BigDecimal popularity;

	@Column(name = "vote_average", precision = 4, scale = 2)
	private BigDecimal voteAverage;

	@Column(name = "vote_count")
	private Integer voteCount;

	private String tagline;
}
