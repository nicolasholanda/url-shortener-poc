package com.nicolasholanda.urlshortener.repository;

import com.nicolasholanda.urlshortener.domain.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortKey(String shortKey);

    @Query("""
            select m from UrlMapping m
            where m.longUrlHash = :hash and m.longUrl = :longUrl
            """)
    Optional<UrlMapping> findByLongUrl(@Param("hash") String hash, @Param("longUrl") String longUrl);

    boolean existsByShortKey(String shortKey);
}
