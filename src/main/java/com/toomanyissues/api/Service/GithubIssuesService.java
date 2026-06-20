package com.toomanyissues.api.Service;



import com.toomanyissues.api.ErrorHandling.exceptions.UserDoesNotExistException;
import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.User;
import com.toomanyissues.api.Service.Helper.LabelMapper;
import com.toomanyissues.api.repository.GithubIssuesRepository;
import com.toomanyissues.api.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;


import java.util.Collection;
import java.util.List;


@Service
public class GithubIssuesService {
    GithubIssuesRepository githubIssuesRepository;
    UserRepository userRepository;
    LabelMapper labelMapper;
    private final MongoTemplate mongoTemplate;
    public GithubIssuesService(GithubIssuesRepository githubIssuesRepository,
                               MongoTemplate mongoTemplate,
                               UserRepository userRepository,
                               LabelMapper labelMapper
                               ) {
        this.githubIssuesRepository = githubIssuesRepository;
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.labelMapper = labelMapper;
    }
    public Page<GithubIssues> getRecommendedIssues(User user,
                                                   List<String> primaryLanguages,
                                                   List<String> labels,
                                                   String keyword,
                                                   Integer page,
                                                   Integer size) {
        User userProfile = userRepository
                .findFullProfileByUsername(user.getUsername())
                .orElseThrow(() -> new UserDoesNotExistException(user.getUsername() + " does not exist"));


        Query query;
        if(keyword != null && !keyword.trim().isEmpty()) {
            TextCriteria criteria = TextCriteria.forDefaultLanguage().matching(keyword);
            query = TextQuery.queryText(criteria).sortByScore();
        }
        else{
            query = new Query();
            query.with(Sort.by(Sort.Direction.DESC, "createdAtGithub"));
        }
        query.addCriteria(Criteria.where("isActive").is(true));
        boolean hasUiLangs = (primaryLanguages != null && !primaryLanguages.isEmpty());
        Collection<String> activeLanguages = hasUiLangs ? primaryLanguages : userProfile.getPrimaryLanguages();
        if (activeLanguages != null && !activeLanguages.isEmpty()) {
            List<String> lowerCaseLanguages = activeLanguages.stream().map(String::toLowerCase).toList();
            query.addCriteria(Criteria.where("primaryLanguage").in(lowerCaseLanguages));
        }
        boolean hasUiLabels = (labels != null && !labels.isEmpty());
        boolean hasPrefLabels = (userProfile.getPreferences() != null && !userProfile.getPreferences().isEmpty());
        if (hasUiLabels && hasPrefLabels) {
            List<String> mappedPref = labelMapper.expandTags(new java.util.ArrayList<>(userProfile.getPreferences()));
            List<String> mappedUi = labelMapper.expandTags(labels);

            query.addCriteria(new Criteria().andOperator(
                    Criteria.where("labels.name").in(mappedPref),
                    Criteria.where("labels.name").in(mappedUi)
            ));
        } else if (hasUiLabels) {
            query.addCriteria(Criteria.where("labels.name").in(labelMapper.expandTags(labels)));
        } else if (hasPrefLabels) {
            query.addCriteria(Criteria.where("labels.name").in(labelMapper.expandTags(new java.util.ArrayList<>(userProfile.getPreferences()))));
        }

        query.skip((long) page * size);
        query.limit(size);
        List<GithubIssues> issues = mongoTemplate.find(query, GithubIssues.class);
        long totalCount = mongoTemplate.count(Query.of(query).skip(-1).limit(-1), GithubIssues.class);
        PageRequest pageRequest = PageRequest.of(page, size);
        return new PageImpl<>(issues, pageRequest, totalCount);
    }
    public Page<GithubIssues> getIssues(
                                        List<String> primaryLanguages,
                                        List<String> labels,
                                        String keyword,
                                        Integer page,
                                        Integer size) {
        Query query;
        if(keyword != null && !keyword.trim().isEmpty()) {
            TextCriteria criteria = TextCriteria.forDefaultLanguage().matching(keyword);
            query = TextQuery.queryText(criteria).sortByScore();
        }
        else{
            query = new Query();
            query.with(Sort.by(Sort.Direction.DESC, "createdAtGithub"));
        }
        query.addCriteria(Criteria.where("isActive").is(true));
        if(labels !=null && !labels.isEmpty()){
            List<String> mappedLabels = labelMapper.expandTags(labels);
            query.addCriteria(Criteria.where("labels.name").in(mappedLabels));
        }
        if(primaryLanguages != null  && !primaryLanguages.isEmpty()){
            List<String> lowerCaseLanguages = primaryLanguages.stream().map(String::toLowerCase).toList();
            query.addCriteria(Criteria.where("primaryLanguage").in(lowerCaseLanguages));
        }

        query.skip((long) page * size);
        query.limit(size);
        List<GithubIssues> issues = mongoTemplate.find(query, GithubIssues.class);
        long totalCount = mongoTemplate.count(Query.of(query).skip(-1).limit(-1), GithubIssues.class);
        PageRequest pageRequest = PageRequest.of(page, size);
        return new PageImpl<>(issues, pageRequest, totalCount);
    }

    public GithubIssues getIssuesById(String id) {
        return githubIssuesRepository
                .findById(id)
                .orElse(null);
    }
    public void deleteIssue(String id) {
        GithubIssues p = githubIssuesRepository.findById(id).orElse(new GithubIssues());
        if(!p.equals(new GithubIssues())){
            System.out.println(p);
            p.setIsActive(false);
            githubIssuesRepository.save(p);
        }
    }



}
