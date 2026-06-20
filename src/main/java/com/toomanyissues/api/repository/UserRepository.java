package com.toomanyissues.api.repository;

import com.toomanyissues.api.Model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    @Query("SELECT l, COUNT(u) FROM User u JOIN u.primaryLanguages l GROUP BY l ORDER BY COUNT(u) DESC")
    List<Object[]> findTopLanguages(Pageable pageable);
    @Query("SELECT p, COUNT(u) FROM User u JOIN u.preferences p GROUP BY p ORDER BY COUNT(u) DESC")
    List<Object[]> findTopPreferences(Pageable pageable);
    @EntityGraph(attributePaths = {"preferences", "primaryLanguages"})
    Optional<User> findFullProfileByUsername(String username);
}

