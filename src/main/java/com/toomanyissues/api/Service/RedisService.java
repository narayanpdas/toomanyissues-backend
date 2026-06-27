package com.toomanyissues.api.Service;


import com.toomanyissues.api.Service.DTOs.RedisDTOs.ScrappedRepoMetadata;
import com.toomanyissues.api.repository.ScrappedRepoInfoRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class RedisService {
    StringRedisTemplate stringRedisTemplate;
    ScrappedRepoInfoRepository scrappedRepoInfoRepository;
    JdbcTemplate jdbcTemplate;
    String INIT_KEY = "Sql:InitStatus:";
    String SQL_KEY_PREFIX = "Sql:Data:";
    String REPO_TYPE_KEY = "Sql:RepoTemp:";
    String DIRTY_REPO_KEY = "Sql:DirtyRepos";
    String DIRTY_REPO_PROCESSING_KEY = "Sql:DirtyRepos:Processing";
    public RedisService(StringRedisTemplate stringRedisTemplate,JdbcTemplate jdbcTemplate ,ScrappedRepoInfoRepository scrappedRepoInfoRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.scrappedRepoInfoRepository = scrappedRepoInfoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean checkSqlInitializationStatus(){
        String value = stringRedisTemplate.opsForValue().get(INIT_KEY);
        if(value == null) {
            return false;
        }
        else return value.equals("TRUE");
    }
    public void setSqlData(){
        List<ScrappedRepoMetadata> scrappedRepoInfos = scrappedRepoInfoRepository.findAllByScrappedRepoMetadata();
        for (ScrappedRepoMetadata scrappedRepoMetadata : scrappedRepoInfos) {
            String key = SQL_KEY_PREFIX + scrappedRepoMetadata.id();
            Map<String, String> values = processRepoContext(scrappedRepoMetadata);
            stringRedisTemplate.opsForHash().putAll(key, values);
            stringRedisTemplate.opsForSet().add(
                    REPO_TYPE_KEY+ scrappedRepoMetadata.activityTemperature(),
                    scrappedRepoMetadata.id().toString());
        }
        stringRedisTemplate.opsForValue().set(INIT_KEY, "TRUE", Duration.ofDays(7));
    }

    private static @NonNull Map<String, String> processRepoContext(ScrappedRepoMetadata scrappedRepoMetadata) {
        Map<String,String> values=new HashMap<>();
        values.put("repoName", scrappedRepoMetadata.repoName());
        values.put("repoUrl", scrappedRepoMetadata.repoUrl());
        values.put("repoType", scrappedRepoMetadata.repoType());
        values.put("repoOwnerName", scrappedRepoMetadata.repoOwnerName());
        values.put("primaryLanguage", scrappedRepoMetadata.primaryLanguage());
        values.put("activityTemperature", scrappedRepoMetadata.activityTemperature());
        values.put("lastIssueSync", scrappedRepoMetadata.lastIssueSync().toString());
        return values;
    }

    public List<Long> getRepoIdsByTemperature(String temperature){
        Set<String> st =  stringRedisTemplate.opsForSet().members(REPO_TYPE_KEY+temperature);
        List<Long> ids = new ArrayList<>();
        if (st != null) {
            st.forEach(id -> ids.add(Long.parseLong(id)));
        }
        return ids;
    }
    public void setRepoByTemperature(String id,String prevTemp,String newTemp){
        stringRedisTemplate.opsForSet().remove(REPO_TYPE_KEY+prevTemp,id);
        stringRedisTemplate.opsForSet().add(REPO_TYPE_KEY+newTemp,id);
    }
    public void updateRepoTemperatureAndSyncTime(String repoId,String temperature,String sync){
        String key = SQL_KEY_PREFIX + repoId;
        Map<Object, Object> updatedValues = new HashMap<>();
        updatedValues.put("activityTemperature",temperature);
        updatedValues.put("lastIssueSync",sync);
        stringRedisTemplate.opsForHash().putAll(key, updatedValues);
    }
    @Transactional
    public void updateAllSqlData(List<String> ids){
        if (ids == null || ids.isEmpty()) {
            System.out.println("[syncSupabase-updateAllSqlData] 0 dirty repos to sync. Skipping DB call.");
            return;
        }
        List<Object> redisResults = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(@NonNull RedisOperations<K, V> operations)
                    throws DataAccessException {
                for (String id : ids) {
                    operations.opsForHash().entries((K)(SQL_KEY_PREFIX + id));
                }
                return null;
            }
        });
        List<Object[]> batchArgs = new ArrayList<>();
        String SQL = "UPDATE scrapped_repo_info SET activity_temperature = ? ,last_issue_sync = ? WHERE id = ?";
        for (int i = 0; i < ids.size(); i++) {
            Map<String, String> mp = (Map<String, String>) redisResults.get(i);
            if (mp != null && !mp.isEmpty()) {
                String temp = mp.get("activityTemperature");
                String syncStr = mp.get("lastIssueSync");
                Timestamp syncTimestamp = Timestamp.from(Instant.parse(syncStr));
                batchArgs.add(new Object[]{temp, syncTimestamp, ids.get(i)});
            }
        }
        jdbcTemplate.batchUpdate(SQL,batchArgs);
        stringRedisTemplate.delete(DIRTY_REPO_PROCESSING_KEY);
    }
    public List<String> getDirtyRepos(){
        Boolean hasDirtyRepos = stringRedisTemplate.hasKey(DIRTY_REPO_KEY);
        if (Boolean.FALSE.equals(hasDirtyRepos)) {
            return new ArrayList<>();
        }
        stringRedisTemplate.rename(DIRTY_REPO_KEY, DIRTY_REPO_PROCESSING_KEY);
        Set<String> members = stringRedisTemplate.opsForSet().members(DIRTY_REPO_PROCESSING_KEY);
        return members != null ? members.stream().toList() : new ArrayList<>();
    }
    public List<Map<String, String>> getBatchRepoData(List<String> repoIds) {
        List<Object> rawResults = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;
                for (String repoId : repoIds) {
                    stringOps.opsForHash().entries(SQL_KEY_PREFIX + repoId);
                }
                return null;
            }
        });
        return getMapList(rawResults);
    }


    private static @NonNull List<Map<String, String>> getMapList(List<Object> rawResults) {
        List<Map<String, String>> results = new ArrayList<>();

        for (Object result : rawResults) {
            Map<String, String> stringMap = new HashMap<>();

            // Safe instance check and cast
            if (result instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    stringMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            results.add(stringMap);
        }
        return results;
    }

    public void markRepoAsDirty(String id) {
        stringRedisTemplate.opsForSet().add(DIRTY_REPO_KEY,id);
    }
}
