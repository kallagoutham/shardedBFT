package com.example.two_phase_pbft.Models;

import lombok.Data;

@Data
public class TransactionRequest {
	private String type;
	private Transaction transaction;
	private long timestamp;
	private String client;
	private String signature;
	private String isIntraShard;
    private String grp;
    
}
