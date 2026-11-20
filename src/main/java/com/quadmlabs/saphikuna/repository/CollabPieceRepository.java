package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.CollabPiece;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollabPieceRepository extends JpaRepository<CollabPiece, UUID> {

    boolean existsByCommissionId(UUID commissionId);
}
