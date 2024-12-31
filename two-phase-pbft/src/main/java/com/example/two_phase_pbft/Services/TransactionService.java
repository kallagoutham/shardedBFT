package com.example.two_phase_pbft.Services;

import java.util.List;

import com.example.two_phase_pbft.Models.Account;
import com.example.two_phase_pbft.Models.CombinedLogs;
import com.example.two_phase_pbft.Models.PrePrepareRequest;
import com.example.two_phase_pbft.Models.PrepareAndCommit;
import com.example.two_phase_pbft.Models.Reply;
import com.example.two_phase_pbft.Models.TransactionRequest;

public interface TransactionService {

	List<Account> PrintDB();
	boolean processTransaction(TransactionRequest transactionRequest);
	PrepareAndCommit processPrePrepareRequest(PrePrepareRequest ppq);
	List<PrePrepareRequest> getPrePrepareLog();
	PrepareAndCommit processPrepareRequest(PrepareAndCommit prepare);
	void processCommitRequest(PrepareAndCommit commit);
	List<PrepareAndCommit> getPrepareLog();
	List<PrepareAndCommit> getCommitLog();
	List<Reply> getExecuted();
	void processOptimisticCommitRequest(PrepareAndCommit prepare);
	void filter();
	void filterPrePrepare();
	String getStatus(int sequenceNumber);
	CombinedLogs getCombinedLogs();
	void processCrossShardTransaction(TransactionRequest transactionRequest);
	void processCrossCommitRequest(PrepareAndCommit commit);
	void processCrossShardPreparePhase(TransactionRequest transactionRequest);
	void processCrossShardCommitPhase(TransactionRequest transactionRequest);
	void processCrossShardCommitPhaseToCluster(TransactionRequest transactionRequest);
	int getBalance(long parseLong);
	List<String> getDataStore();
	void abortMessages(PrePrepareRequest prepare);
	boolean processDoubleTransaction(TransactionRequest transaction);

}
