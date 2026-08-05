package com.chagok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChagokApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChagokApplication.class, args);
	}

}
