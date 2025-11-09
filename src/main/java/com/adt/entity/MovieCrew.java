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
@Table(name = "movie_crew", uniqueConstraints = @UniqueConstraint(name = "uq_movie_crew", columnNames = { "movie_id",
		"person_id", "job_id" }))
@Getter
@Setter
public class MovieCrew {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "movie_id", nullable = false)
	private Integer movieId;

	@Column(name = "person_id", nullable = false)
	private Integer personId;

	@Column(name = "job_id", nullable = false)
	private Integer jobId;
}
