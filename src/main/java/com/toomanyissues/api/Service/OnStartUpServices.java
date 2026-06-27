package com.toomanyissues.api.Service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

@Service
public class OnStartUpServices implements ApplicationListener<ApplicationReadyEvent> {
    RedisService redisService;
    public OnStartUpServices(RedisService redisService) {
        this.redisService = redisService;
    }
    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        // Redis calls and Supabase calls(only if redis:sql part is empty)
        if(!redisService.checkSqlInitializationStatus()){
            redisService.setSqlData();
        }
    }

}
