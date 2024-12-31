package com.example.two_phase_pbft.Models;

import lombok.Data;

@Data
public class PrepareAndCommit {
	private String type;
	private int v;
	private int n;
	private String digest;
	private Transaction message;
	private String signature;
	private int i;
	
}
