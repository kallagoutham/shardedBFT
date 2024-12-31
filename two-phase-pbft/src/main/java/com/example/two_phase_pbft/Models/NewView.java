package com.example.two_phase_pbft.Models;

import java.util.List;

import lombok.Data;

@Data
public class NewView {

	private String type;
	private int view;
	private String digest;
	private String signature;
	List<PrePrepareRequest> o;
	List<ViewChange> v;
	
}
