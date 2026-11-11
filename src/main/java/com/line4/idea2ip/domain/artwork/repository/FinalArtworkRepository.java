package com.line4.idea2ip.domain.artwork.repository;

import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalArtworkRepository extends JpaRepository<FinalArtwork, Long> {

    boolean existsByProject_ProjectId(Long projectId);

    Optional<FinalArtwork> findByProject_ProjectId(Long projectId);
}
