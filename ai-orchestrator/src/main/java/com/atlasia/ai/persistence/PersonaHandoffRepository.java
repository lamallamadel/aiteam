package com.atlasia.ai.persistence;

import com.atlasia.ai.model.PersonaHandoffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PersonaHandoffRepository extends JpaRepository<PersonaHandoffEntity, UUID> {

    List<PersonaHandoffEntity> findByUserIdAndStatus(String userId, String status);

    List<PersonaHandoffEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
