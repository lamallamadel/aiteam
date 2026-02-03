package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunRepository extends JpaRepository<RunEntity, UUID> {}
