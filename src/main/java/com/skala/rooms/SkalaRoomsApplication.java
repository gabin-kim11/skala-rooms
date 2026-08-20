package com.skala.rooms;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkalaRoomsApplication {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(SEOUL));
        SpringApplication.run(SkalaRoomsApplication.class, args);
    }
}
