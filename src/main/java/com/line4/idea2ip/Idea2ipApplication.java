package com.line4.idea2ip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class Idea2ipApplication {

	public static void main(String[] args) {
		SpringApplication.run(Idea2ipApplication.class, args);
	}

}
