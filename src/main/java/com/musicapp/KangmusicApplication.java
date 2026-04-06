package com.musicapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KangmusicApplication {

	public static void main(String[] args) {
		SpringApplication.run(KangmusicApplication.class, args);
	}
	
}
