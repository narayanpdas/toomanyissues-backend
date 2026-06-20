package com.toomanyissues.api.TestingGround;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController("/ping")
public class TestController {
    private final List<Integer> pings = new ArrayList<Integer>();


    @GetMapping("")
    public List<Integer> ping() {
        return pings;
    }
    @PostMapping("/{node_id}")
    public void add_ping(@PathVariable Integer id){
        pings.add(id);
    }
    @PutMapping("/{idx}")
    public Integer update_ping(@PathVariable Integer idx){
        if(idx<pings.size()){
            return pings.get(idx);
        }
        else throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @DeleteMapping("/{idx}")
    public void delete_ping(@PathVariable Integer idx){
        if(idx<pings.size())pings.remove((int) idx);
        else throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
