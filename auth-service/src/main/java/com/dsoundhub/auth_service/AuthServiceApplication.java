package com.dsoundhub.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		loadDotenv(".");
		loadDotenv("..");
		SpringApplication.run(AuthServiceApplication.class, args);
	}

	private static void loadDotenv(String directory) {
		Dotenv dotenv = Dotenv.configure()
				.directory(directory)
				.ignoreIfMissing()
				.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
	}

}
