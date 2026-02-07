package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RunArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunArtifactRepository extends JpaRepository<RunArtifactEntity, UUID> {}
