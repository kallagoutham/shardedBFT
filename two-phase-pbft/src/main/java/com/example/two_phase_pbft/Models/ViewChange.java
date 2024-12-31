package com.example.two_phase_pbft.Models;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ViewChange {

	private String type;
	private int view;
	private int n;
	private List<PrepareAndCommit> checkPointCertificate = new ArrayList<>();
	private int i;
	private String digest;
	private String signature;
	
}
