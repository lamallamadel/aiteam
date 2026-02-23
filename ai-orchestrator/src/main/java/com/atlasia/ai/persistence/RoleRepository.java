package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Integer> {
    
    Optional<RoleEntity> findByName(String name);
    
    boolean existsByName(String name);
    
    @Query("SELECT r FROM RoleEntity r LEFT JOIN FETCH r.permissions WHERE r.name = :name")
    Optional<RoleEntity> findByNameWithPermissions(String name);
}
