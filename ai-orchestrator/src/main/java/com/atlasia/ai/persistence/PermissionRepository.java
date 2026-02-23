package com.atlasia.ai.persistence;

import com.atlasia.ai.model.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Integer> {
    
    Optional<PermissionEntity> findByResourceAndAction(String resource, String action);
    
    boolean existsByResourceAndAction(String resource, String action);
}
