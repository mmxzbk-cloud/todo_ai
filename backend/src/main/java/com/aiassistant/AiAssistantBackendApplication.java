package com.aiassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiAssistantBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiAssistantBackendApplication.class, args);
	}

}
