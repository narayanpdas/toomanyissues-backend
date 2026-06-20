package com.toomanyissues.api.repository;

import com.toomanyissues.api.Model.ScrappedRepoInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrappedRepoInfoRepository
        extends JpaRepository<ScrappedRepoInfo,Long> {
    boolean existsByRepoName(String repoName);
    List<ScrappedRepoInfo> findByRepoNameIgnoreCase(String ownerName);
    ScrappedRepoInfo findByRepoName(String repoName);
    List<ScrappedRepoInfo> findByRepoType(String repoType);
    List<ScrappedRepoInfo> findTop10ByRawReadmeIsNotNullAndAiSummaryStatusAndRepoType(String aiSummaryStatus,
                                                                                      String repoType);
    List<ScrappedRepoInfo> findTop50ByRawReadmeIsNull();
    long countByRepoType(String repoType);
    Page<ScrappedRepoInfo> findByRepoType(String repoType, Pageable pageable);
}
