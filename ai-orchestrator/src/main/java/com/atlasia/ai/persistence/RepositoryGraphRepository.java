package com.atlasia.ai.persistence;

import com.atlasia.ai.model.RepositoryGraphEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryGraphRepository extends JpaRepository<RepositoryGraphEntity, UUID> {

    Optional<RepositoryGraphEntity> findByRepoUrl(String repoUrl);

    List<RepositoryGraphEntity> findByWorkspaceType(String workspaceType);

    @Query(value = "SELECT * FROM repository_graph WHERE dependencies::jsonb @> to_jsonb(?1::text)", nativeQuery = true)
    List<RepositoryGraphEntity> findByDependsOn(String repoUrl);

    @Query(value = "SELECT DISTINCT repo_url FROM repository_graph", nativeQuery = true)
    List<String> findAllRepoUrls();
}
