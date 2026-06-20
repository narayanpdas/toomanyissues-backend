package com.toomanyissues.api.repository;

import com.toomanyissues.api.Model.GithubIssues;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GithubIssuesRepository
        extends MongoRepository<GithubIssues, String> {

}
