package com.hh;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetContoller {
    //http://localhost:8080/down/https:shushuvod1.vodyutu.comshu20221009shuz1TyMUuyshu1000kbshuhlsshuindex.m3u8
    @GetMapping("/down/{url}")
    public void hi(@PathVariable String url) {
        new Thread(()->{
            String shu = url.replace("shu", "/");
            System.out.println("============="+shu);
            M3u8Download.down(shu);
        }).run();

    }

}
