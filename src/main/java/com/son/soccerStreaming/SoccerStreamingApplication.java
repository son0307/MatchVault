package com.son.soccerStreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SoccerStreamingApplication {

	public static void main(String[] args) {
		SpringApplication.run(SoccerStreamingApplication.class, args);
	}

}
