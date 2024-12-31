package com.example.two_phase_pbft.Models;

import lombok.Data;

@Data
public class PTC {
	PrepareAndCommit prepareAndCommit;
	TransactionRequest transactionRequest;
}
