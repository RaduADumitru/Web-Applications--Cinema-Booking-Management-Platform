package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {

	@Query(value = "select * from movies where movie_id = :id", nativeQuery = true)
	Movie findByIdIncludingDeleted(@Param("id") Long id);
}
