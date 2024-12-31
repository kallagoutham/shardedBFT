package com.example.two_phase_pbft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TwoPhasePbftApplication {

	public static void main(String[] args) {
		SpringApplication.run(TwoPhasePbftApplication.class, args);
		System.out.println("Hello from two phase pbft server...");
	}

}
