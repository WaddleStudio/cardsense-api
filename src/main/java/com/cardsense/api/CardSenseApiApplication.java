package com.cardsense.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CardSenseApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardSenseApiApplication.class, args);
	}

}
