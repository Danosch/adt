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

@Entity
@Table(name = "movie_spoken_language", uniqueConstraints = @UniqueConstraint(name = "uq_msl", columnNames = {
		"movie_id", "iso_639_1" }))
@Getter
@Setter
public class MovieSpokenLanguage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "iso_639_1", length = 2, nullable = false)
	private String iso6391;
}
