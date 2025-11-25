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
 * Konkreter Job innerhalb einer Department, z. B. Director oder Writer.
 */
@Entity
@Table(name = "job", uniqueConstraints = @UniqueConstraint(name = "uq_job_dept", columnNames = { "department_id",
                "name" }))
@Getter
@Setter
public class Job {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "department_id", nullable = false)
	private Integer departmentId;

	@Column(nullable = false)
	private String name;
}
