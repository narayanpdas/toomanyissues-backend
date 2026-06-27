package com.toomanyissues.api.repository;

import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs.RepoReadmeDTO;
import com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs.SummarizationMetaData;
import com.toomanyissues.api.Service.DTOs.RedisDTOs.ScrappedRepoMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScrappedRepoInfoRepository
        extends JpaRepository<ScrappedRepoInfo,Long> {
    boolean existsByRepoName(String repoName);
    List<ScrappedRepoInfo> findByRepoNameIgnoreCase(String ownerName);

    // TODO CHANGE THIS WHEN DEPLOYING The scrapped_repo_info class name
    @Query("SELECT new com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs.SummarizationMetaData(r.rawReadme,r.aiSummary,r.aiSummaryStatus) FROM scrapped_repo_info r WHERE r.repoName=:repoName")
    SummarizationMetaData findByRepoName(@Param("repoName") String repoName);
    List<ScrappedRepoInfo> findByRepoType(String repoType);
    List<RepoReadmeDTO> findTop10ByRawReadmeIsNotNullAndAiSummaryStatusAndActivityTemperature(String status, String temperature);
    // TODO CHANGE THIS WHEN DEPLOYING The scrapped_repo_info class name
    @Query("SELECT new com.toomanyissues.api.Service.DTOs.RedisDTOs.ScrappedRepoMetadata(r.id,r.repoOwnerName,r.repoName,r.repoUrl,r.repoType,r.primaryLanguage,r.lastIssueSync,r.activityTemperature) FROM scrapped_repo_info r")
    List<ScrappedRepoMetadata> findAllByScrappedRepoMetadata();
    List<ScrappedRepoInfo> findTop50ByRawReadmeIsNull();
    long countByRepoType(String repoType);
    Page<ScrappedRepoInfo> findByRepoType(String repoType, Pageable pageable);
    // TODO CHANGE THIS WHEN DEPLOYING The scrapped_repo_info class name
    @Modifying
    @Transactional
    @Query("UPDATE scrapped_repo_info r SET r.aiSummary = :summary, r.aiSummaryStatus = :status WHERE r.repoName = :repoName")
    void updateRepoSummaryStatus(@Param("repoName") String repoName,
                                 @Param("summary") String summary,
                                 @Param("status") String status);
    // TODO CHANGE THIS WHEN DEPLOYING The scrapped_repo_info class name
    @Modifying
    @Transactional
    @Query("UPDATE scrapped_repo_info r SET r.activityTemperature=:temp ,r.lastIssueSync=:sync WHERE r.id=:id")
    void updateTemperatureAndLastIssueSync(
            @Param("id") String id,
            @Param("temp") String temp,
            @Param("sync")Instant sync
    );
    // TODO CHANGE THIS WHEN DEPLOYING The scrapped_repo_info class name
    @Modifying
    @Transactional
    @Query("UPDATE scrapped_repo_info r SET r.rawReadme = COALESCE(r.rawReadme, :readme), r.aiSummary = :summary, r.aiSummaryStatus = :status WHERE r.repoName = :repoName")
    void updateSummaryInfo(
            @Param("repoName") String repoName,
            @Param("readme") String readme,
            @Param("summary") String summary,
            @Param("status") String status
    );
}
