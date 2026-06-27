package com.toomanyissues.api.Service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BackgroundProcess {
    RedisService redisService;
    public BackgroundProcess(RedisService redisService) {
        this.redisService = redisService;
    }

    @Scheduled(fixedDelay = 7200000 , initialDelay = 3600000) // Syncing every 2 hours, starts after 1 hour of startup
    public void syncSupabase() {
        try {
            List<String> dirtyIds = redisService.getDirtyRepos();
            if (!dirtyIds.isEmpty()) {
                redisService.updateAllSqlData(dirtyIds);
            } else {
                System.out.println("[syncSupabase] Cache is perfectly synced. No dirty repos found.");
            }
        }
        catch (Exception e){
            System.out.println("[syncSupabase] [FAILED]"+"\n ERROR: "+e.getMessage());
        }
    }

}
