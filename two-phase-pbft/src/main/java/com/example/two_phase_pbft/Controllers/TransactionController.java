package com.example.two_phase_pbft.Controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.two_phase_pbft.Models.CombinedLogs;
import com.example.two_phase_pbft.Models.PrePrepareRequest;
import com.example.two_phase_pbft.Models.PrepareAndCommit;
import com.example.two_phase_pbft.Models.Reply;
import com.example.two_phase_pbft.Models.TransactionRequest;
import com.example.two_phase_pbft.Services.TransactionService;

@RestController
public class TransactionController {

	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		super();
		this.transactionService = transactionService;
	}
	
	@GetMapping("/bank/balance")
	public int getBankBalance(@RequestParam(name = "accountId")String accountId) {
		return transactionService.getBalance(Long.parseLong(accountId));
	}
	
	@PostMapping("/bank/cross/transaction")
	public void processCrossShardTransaction(@RequestBody TransactionRequest transactionRequest){
		transactionService.processCrossShardTransaction(transactionRequest);
	}
	
	@PostMapping("/bank/cross/prepare")
	public void processCrossShardPreparePhase(@RequestBody TransactionRequest transactionRequest) {
		transactionService.processCrossShardPreparePhase(transactionRequest);
	}
	
	@PostMapping("/bank/cross/commit")
	public void processCrossShardCommitPhase(@RequestBody TransactionRequest transactionRequest) {
		transactionService.processCrossShardCommitPhase(transactionRequest);
	}
	
	@PostMapping("/bank/cross/abort")
	public void processAbortTransaction(@RequestBody PrePrepareRequest preprepare) {
		transactionService.abortMessages(preprepare);
	}
	
	@PostMapping("/bank/cross/to/commit")
	public void processCrossShardCommitPhaseToCluster(@RequestBody TransactionRequest transactionRequest) {
		transactionService.processCrossShardCommitPhaseToCluster(transactionRequest);
	}

	@PostMapping("/bank/transaction")
	public ResponseEntity<String> processTransaction(@RequestBody TransactionRequest transaction) {
		if (transactionService.processTransaction(transaction)) {
			return ResponseEntity.status(200).body("Transaction Processed");
		}
		return ResponseEntity.status(501).body("Unable to process Transaction");
	}

	@PostMapping("/preprepare")
	public PrepareAndCommit processPrePrepareRequest(@RequestBody PrePrepareRequest ppq) {
		return transactionService.processPrePrepareRequest(ppq);
	}

	@PostMapping("/prepare")
	public PrepareAndCommit processPrepareRequest(@RequestBody PrepareAndCommit prepare) {
		return transactionService.processPrepareRequest(prepare);
	}

	@PostMapping("/optimisticcommit")
	public void processOptimisticCommitRequest(@RequestBody PrepareAndCommit prepare) {
		transactionService.processOptimisticCommitRequest(prepare);
	}

	@PostMapping("/commit")
	public void processCommitRequest(@RequestBody PrepareAndCommit commit) {
		transactionService.processCommitRequest(commit);
	}
	
	@PostMapping("/cross/commit")
	public void processCrossCommitRequest(@RequestBody PrepareAndCommit commit) {
		transactionService.processCrossCommitRequest(commit);
	}

	@GetMapping("/log/preprepare")
	public List<PrePrepareRequest> getPrePreareLog() {
		return transactionService.getPrePrepareLog();
	}

	@GetMapping("/log/prepare")
	public List<PrepareAndCommit> getPrepareLog() {
		return transactionService.getPrepareLog();
	}

	@GetMapping("/log/commit")
	public List<PrepareAndCommit> getCommitLog() {
		return transactionService.getCommitLog();
	}

	@GetMapping("/log/executed")
	public List<Reply> getExecuted() {
		return transactionService.getExecuted();
	}

	@GetMapping("/logs/all")
	public CombinedLogs getAllLogs() {
		return transactionService.getCombinedLogs();
	}

	@PostMapping("/f/pp")
	public void filter() {
		transactionService.filterPrePrepare();
	}
	
	@GetMapping("/datastore")
	public List<String> getDataStore(){
		return transactionService.getDataStore();
	}
	
	@PostMapping("/bank/transaction/double")
	public ResponseEntity<String> processDoubleTransaction(@RequestBody TransactionRequest transaction) {
		if (transactionService.processDoubleTransaction(transaction)) {
			return ResponseEntity.status(200).body("Double Transaction Processed");
		}
		return ResponseEntity.status(501).body("Unable to process Double Transaction");
	} 
	
}
