package com.son.soccerStreaming;

import org.springframework.boot.SpringApplication;

public class TestSoccerStreamingApplication {

	public static void main(String[] args) {
		SpringApplication.from(SoccerStreamingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
