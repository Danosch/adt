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
@Table(name = "language")
@Getter
@Setter
public class Language {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "iso_639_1", length = 2, unique = true, nullable = false)
	private String iso6391;

	@Column(name = "english_name")
	private String englishName;

	private String name;
}
