package com.example.two_phase_pbft.Controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/hello")
	public ResponseEntity<String> health() {
		return ResponseEntity.status(200).body("Hello from two phase commit with linear pbft server");
	}

}
