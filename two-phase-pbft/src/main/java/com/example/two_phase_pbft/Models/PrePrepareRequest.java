package com.example.two_phase_pbft.Models;

import lombok.Data;

@Data
public class PrePrepareRequest {
	private String type;
	private int v;
	private int n;
	private String digest;
	private Transaction message;
	private String signature;
}
