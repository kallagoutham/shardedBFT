package com.example.two_phase_pbft.Models;

import lombok.Data;

@Data
public class ReceiveKey {
	
	int server;
	String publicKey; 
	
}