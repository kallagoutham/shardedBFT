package com.example.two_phase_pbft.Services;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.two_phase_pbft.GlobalVariables.Variables;
import com.example.two_phase_pbft.Models.Account;
import com.example.two_phase_pbft.Models.CombinedLogs;
import com.example.two_phase_pbft.Models.PTC;
import com.example.two_phase_pbft.Models.PrePrepareRequest;
import com.example.two_phase_pbft.Models.PrepareAndCommit;
import com.example.two_phase_pbft.Models.Reply;
import com.example.two_phase_pbft.Models.Transaction;
import com.example.two_phase_pbft.Models.TransactionRequest;
import com.example.two_phase_pbft.Repository.AccountRepository;
import com.example.two_phase_pbft.Utils.DigitalSignatureUtil;
import com.example.two_phase_pbft.Utils.HashUtils;
import com.example.two_phase_pbft.Utils.PeerUtils;
import com.example.two_phase_pbft.Utils.ThresholdSignatureUtil;

@Service
public class TransactionServiceImpl implements TransactionService {

	private final Variables variables;
	private final HashUtils hashUtils;
	private final DigitalSignatureUtil digitalSignatureUtil;
	private final PeerUtils peerUtils;
	private final RestTemplate restTemplate;
	private final ThresholdSignatureUtil thresholdSignatureUtil;
	private final AccountRepository accountRepository;
	private final PerformanceService performanceService;
	Set<Integer> localExecutedTransactions = new HashSet<>();
	int timeoutInSeconds = 2;

	public TransactionServiceImpl(Variables variables, HashUtils hashUtils, DigitalSignatureUtil digitalSignatureUtil,
			PeerUtils peerUtils, RestTemplate restTemplate, ThresholdSignatureUtil thresholdSignatureUtil,
			AccountRepository accountRepository, PerformanceService performanceService) {
		super();
		this.variables = variables;
		this.hashUtils = hashUtils;
		this.digitalSignatureUtil = digitalSignatureUtil;
		this.peerUtils = peerUtils;
		this.restTemplate = restTemplate;
		this.thresholdSignatureUtil = thresholdSignatureUtil;
		this.accountRepository = accountRepository;
		this.performanceService = performanceService;
	}

	@Override
	public void processCrossShardTransaction(TransactionRequest transactionRequest) {
		transactionRequest.getTransaction().setGrp("from");
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		Runnable retryTask = new Runnable() {
			@Override
			public void run() {
				try {
					retryCrossShardTransactionQuorumNotReached();
					if (peerUtils.quorumReachedOrNot()) {
						scheduler.shutdown();
						System.out.println("Quorum reached. Stopping retry task.");
					}
				} catch (Exception e) {
				}
			}
		};
		scheduler.scheduleAtFixedRate(retryTask, 0, 10, TimeUnit.SECONDS);

		if (verifyRequestSignature(transactionRequest)
				&& (!variables.getLocks().contains(transactionRequest.getTransaction().getSender()))
				&& accountRepository.getBalanceByAccount(transactionRequest.getTransaction()
						.getSender()) >= transactionRequest.getTransaction().getAmount()) {
			variables.getLocks().add(transactionRequest.getTransaction().getSender());
			PrePrepareRequest ppr = generatePrePrepareMessage(transactionRequest);
			List<PrepareAndCommit> replies = PrePreparePhase(ppr);
			variables.getPreprepare().add(ppr);
			PrepareAndCommit prepare = generatePrepareMessage(ppr);
			if (peerUtils.quorumReachedOrNot()) {
				variables.getPrepare().add(prepare);
				if (!peerUtils.isByzantineServer()) {
					replies = PreparePhase(prepare, replies);
					if (peerUtils.quorumReachedOrNot()) {
						PrepareAndCommit commit = generateCommitMessage(prepare);
						CrossCommitPhase(commit, replies);
						variables.getWAL().put(variables.getDataStoreCounter(), commit.getMessage());
						executeTransaction(transactionRequest);
						TransactionRequest tr = GenerateTransactionRequestClone(transactionRequest, "to");
						sendCrossShardPreparePhase(tr, replies);
					}
				}
			} else {
				PTC ptc = new PTC();
				ptc.setPrepareAndCommit(prepare);
				ptc.setTransactionRequest(transactionRequest);
				variables.getPtc().add(ptc);
			}
		}
		return;
	}

	private void retryCrossShardTransactionQuorumNotReached() {
		List<PrepareAndCommit> replies = new ArrayList<>();
		for (PTC ptc : variables.getPtc()) {
			if (peerUtils.quorumReachedOrNot()) {
				variables.getPrepare().add(ptc.getPrepareAndCommit());
				if (!peerUtils.isByzantineServer()) {
					replies = PreparePhase(ptc.getPrepareAndCommit(), replies);
					if (peerUtils.quorumReachedOrNot()) {
						PrepareAndCommit commit = generateCommitMessage(ptc.getPrepareAndCommit());
						// here we have to exxecute n
						CrossCommitPhase(commit, replies);
						TransactionRequest tr = GenerateTransactionRequestClone(ptc.getTransactionRequest(), "to");
						sendCrossShardPreparePhase(tr, replies);
					}
				}
			}
		}
		if (peerUtils.quorumReachedOrNot()) {
			variables.getPtc().clear();
		}
		return;
	}

	private void sendAbortMessages(PrePrepareRequest ppr, String group) {
		performanceService.logTaskStart();
		if ("coordinator".equals(group)) {
			@SuppressWarnings("unused")
			List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getPeersOf(ppr.getMessage().getSender())
					.stream().map(url -> CompletableFuture.supplyAsync(() -> {
						try {
							System.out.println("abort message to " + url);
							return restTemplate.postForObject("http://" + url + "/api/bank/cross/abort", ppr,
									PrepareAndCommit.class);
						} catch (Exception e) {
							return null;
						}
					}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
		} else {
			@SuppressWarnings("unused")
			List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getPeersOf(ppr.getMessage().getReceiver())
					.stream().map(url -> CompletableFuture.supplyAsync(() -> {
						try {
							return restTemplate.postForObject("http://" + url + "/api/bank/cross/abort", ppr,
									PrepareAndCommit.class);
						} catch (Exception e) {
							return null;
						}
					}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
		}
		performanceService.logTaskEnd(10);
		return;
	}

	@Override
	public synchronized void abortMessages(PrePrepareRequest ppr) {
		if (!peerUtils.isByzantineServer()) {
			variables.getDataStore().add(variables.getDataStoreCounter() + " A " + " (" + ppr.getMessage().getSender()
					+ "," + ppr.getMessage().getReceiver() + "," + ppr.getMessage().getAmount() + ")");
			variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
			reverseTransaction(ppr);
		}
		variables.getPendingTransactions().remove(variables.getLastExecutedN());
		localExecutedTransactions.add(variables.getLastExecutedN());
		return;
	}

	private TransactionRequest GenerateTransactionRequestClone(TransactionRequest transactionRequest, String string) {
		TransactionRequest tr = new TransactionRequest();
		Transaction t = new Transaction();
		t.setSender(transactionRequest.getTransaction().getSender());
		t.setReceiver(transactionRequest.getTransaction().getReceiver());
		t.setAmount(transactionRequest.getTransaction().getAmount());
		t.setTimestamp(transactionRequest.getTransaction().getTimestamp());
		t.setIsIntraShard(transactionRequest.getTransaction().getIsIntraShard());
		t.setGrp(string);
		t.setServer(transactionRequest.getTransaction().getServer());

		tr.setClient(transactionRequest.getClient());
		tr.setGrp(string);
		tr.setIsIntraShard(transactionRequest.getIsIntraShard());
		tr.setSignature(transactionRequest.getSignature());
		tr.setTimestamp(transactionRequest.getTimestamp());
		tr.setTransaction(t);
		tr.setType(transactionRequest.getType());
		return tr;
	}

	@Override
	public void processCrossShardCommitPhase(TransactionRequest transactionRequest) {
		executeTransaction(transactionRequest);
		if (!peerUtils.isByzantineServer()) {
			variables.getDataStore()
					.add(variables.getDataStoreCounter() + " C " + "(" + transactionRequest.getTransaction().getSender()
							+ "," + transactionRequest.getTransaction().getReceiver() + ","
							+ transactionRequest.getTransaction().getAmount() + ")");
			variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
		}
		variables.getLocks().remove(transactionRequest.getTransaction().getSender());
		if (peerUtils.isPrimaryServerForCluster()) {
			TransactionRequest tr = GenerateTransactionRequestClone(transactionRequest, "to");
			sendCrossShardCommitPhaseToCluster(tr);
		}
		return;
	}

	@Override
	public void processCrossShardCommitPhaseToCluster(TransactionRequest transactionRequest) {
		executeTransaction(transactionRequest);
		if (!peerUtils.isByzantineServer()) {
			variables.getDataStore()
					.add(variables.getDataStoreCounter() + " C " + "(" + transactionRequest.getTransaction().getSender()
							+ "," + transactionRequest.getTransaction().getReceiver() + ","
							+ transactionRequest.getTransaction().getAmount() + ")");
			variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
		}
		variables.getLocks().remove(transactionRequest.getTransaction().getReceiver());
		return;
	}

	private void sendCrossShardCommitPhaseToCluster(TransactionRequest transactionRequest) {
		performanceService.logTaskStart();
		@SuppressWarnings("unused")
		List<CompletableFuture<PrepareAndCommit>> futures = peerUtils
				.getPeersOf(transactionRequest.getTransaction().getReceiver()).stream()
				.map(url -> CompletableFuture.supplyAsync(() -> {
					try {
						return restTemplate.postForObject("http://" + url + "/api/bank/cross/to/commit",
								transactionRequest, PrepareAndCommit.class);
					} catch (Exception e) {
						return null;
					}
				}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		allFutures.join();
		performanceService.logTaskEnd(10);
		return;
	}

	@Override
	public void processCrossShardPreparePhase(TransactionRequest transactionRequest) {
		if (verifyRequestSignature(transactionRequest)
				&& (!variables.getLocks().contains(transactionRequest.getTransaction().getReceiver()))) {
			if (variables.isDisConnectedServer()) {
				variables.incrementCounter();
				return;
			}
			List<PrepareAndCommit> replies = new ArrayList<>();
			variables.getLocks().add(transactionRequest.getTransaction().getReceiver());
			PrePrepareRequest ppr = generatePrePrepareMessage(transactionRequest);
			variables.getPreprepare().add(ppr);
			PrepareAndCommit prepare = generatePrepareMessage(ppr);
			if (peerUtils.quorumReachedOrNot()) {
				variables.getPrepare().add(prepare);
				if (!peerUtils.isByzantineServer()) {
					if (peerUtils.quorumReachedOrNot()) {
						PrepareAndCommit commit = generateCommitMessage(prepare);
						commit.setI(peerUtils.getServerPort());
						if (!variables.getCommitted().contains(commit)) {
							variables.getCommitted().add(commit);
							variables.getDataStore()
									.add(variables.getDataStoreCounter() + " P " + " ("
											+ commit.getMessage().getSender() + "," + commit.getMessage().getReceiver()
											+ "," + commit.getMessage().getAmount() + ")");
							variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
						}
						TransactionRequest tr = GenerateTransactionRequestClone(transactionRequest, "from");
						if (peerUtils.isPrimaryServerForCluster()) {
							sendCrossShardCommitPhase(tr, replies);
						}
					}
				}
			} else {
				// receiver side quorum not reached...
				if (peerUtils.isPrimaryServerForCluster()) {
					sendAbortMessages(ppr, "coordinator");
//					sendAbortMessages(ppr, "participant");
				}
			}
		}
		return;
	}

	private void sendCrossShardCommitPhase(TransactionRequest tr, List<PrepareAndCommit> replies) {
		performanceService.logTaskStart();
		@SuppressWarnings("unused")
		List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getPeersOf(tr.getTransaction().getSender())
				.stream().map(url -> CompletableFuture.supplyAsync(() -> {
					try {
						return restTemplate.postForObject("http://" + url + "/api/bank/cross/commit", tr,
								PrepareAndCommit.class);
					} catch (Exception e) {
						return null;
					}
				}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		allFutures.join();
		performanceService.logTaskEnd(10);
		return;
	}

	private void sendCrossShardPreparePhase(TransactionRequest tr, List<PrepareAndCommit> replies) {
		performanceService.logTaskStart();
		boolean isAuthorized = thresholdSignatureUtil.verifyThresholdSignatures(replies,
				2 * variables.getFaultsTolerated());
		if (isAuthorized) {
			@SuppressWarnings("unused")
			List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getPeersOf(tr.getTransaction().getReceiver())
					.stream().map(url -> CompletableFuture.supplyAsync(() -> {
						try {
							return restTemplate.postForObject("http://" + url + "/api/bank/cross/prepare", tr,
									PrepareAndCommit.class);
						} catch (Exception e) {
							return null;
						}
					}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
		}
		performanceService.logTaskEnd(10);
		return;
	}

	@Override
	public boolean processTransaction(TransactionRequest transactionRequest) {
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		Runnable retryTask = new Runnable() {
			@Override
			public void run() {
				try {
					retryQuorumTransactions();
					if (peerUtils.quorumReachedOrNot()) {
						scheduler.shutdown();
						System.out.println("Quorum reached. Stopping retry task.");
					}
				} catch (Exception e) {
				}
			}
		};
		scheduler.scheduleAtFixedRate(retryTask, 0, 10, TimeUnit.SECONDS);

		if (verifyRequestSignature(transactionRequest)
				&& (!variables.getLocks().contains(transactionRequest.getTransaction().getSender())
						&& !variables.getLocks().contains(transactionRequest.getTransaction().getReceiver()))
				&& accountRepository.getBalanceByAccount(transactionRequest.getTransaction()
						.getSender()) >= transactionRequest.getTransaction().getAmount()) {
			variables.getLocks().add(transactionRequest.getTransaction().getSender());
			variables.getLocks().add(transactionRequest.getTransaction().getReceiver());
			PrePrepareRequest ppr = generatePrePrepareMessage(transactionRequest);
			List<PrepareAndCommit> replies = PrePreparePhase(ppr);
			variables.getPreprepare().add(ppr);
			PrepareAndCommit prepare = generatePrepareMessage(ppr);
			if (peerUtils.quorumReachedOrNot()) {
				variables.getPrepare().add(prepare);
				if (!peerUtils.isByzantineServer()) {
					replies = PreparePhase(prepare, replies);
					if (peerUtils.quorumReachedOrNot()) {
						PrepareAndCommit commit = generateCommitMessage(prepare);
						CommitPhase(commit, replies);
						variables.getLocks().remove(transactionRequest.getTransaction().getSender());
						variables.getLocks().remove(transactionRequest.getTransaction().getReceiver());
						return true;
					}
				}
			} else {
				variables.getPt().add(prepare);
				return true;
			}
		}
		return false;
	}

	private void CommitPhase(PrepareAndCommit commit, List<PrepareAndCommit> replies) {
		performanceService.logTaskStart();
		boolean isAuthorized = thresholdSignatureUtil.verifyThresholdSignatures(replies,
				2 * variables.getFaultsTolerated());
		if (isAuthorized || peerUtils.quorumReachedOrNot()) {
			@SuppressWarnings("unused")
			List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getAllServersList().stream()
					.map(url -> CompletableFuture.supplyAsync(() -> {
						try {
							return restTemplate.postForObject("http://" + url + "/api/commit", commit,
									PrepareAndCommit.class);
						} catch (Exception e) {
							return null;
						}
					}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
		}
		performanceService.logTaskEnd(10);
		return;
	}

	private void CrossCommitPhase(PrepareAndCommit commit, List<PrepareAndCommit> replies) {
		performanceService.logTaskStart();
		boolean isAuthorized = thresholdSignatureUtil.verifyThresholdSignatures(replies,
				2 * variables.getFaultsTolerated());
		if (isAuthorized || peerUtils.quorumReachedOrNot()) {
			@SuppressWarnings("unused")
			List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getAllServersList().stream()
					.map(url -> CompletableFuture.supplyAsync(() -> {
						try {
							return restTemplate.postForObject("http://" + url + "/api/cross/commit", commit,
									PrepareAndCommit.class);
						} catch (Exception e) {
							return null;
						}
					}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
		}
		performanceService.logTaskEnd(10);
		return;
	}

	private List<PrepareAndCommit> PreparePhase(PrepareAndCommit prepare, List<PrepareAndCommit> replies) {
		performanceService.logTaskStart();
		boolean isAuthorized = thresholdSignatureUtil.verifyThresholdSignatures(replies,
				2 * variables.getFaultsTolerated());
		if (isAuthorized || peerUtils.quorumReachedOrNot()) {
			List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getPeersList().stream()
					.map(url -> CompletableFuture.supplyAsync(() -> {
						try {
							return restTemplate.postForObject("http://" + url + "/api/prepare", prepare,
									PrepareAndCommit.class);
						} catch (Exception e) {
							return null;
						}
					}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
			performanceService.logTaskEnd(10);
			return futures.stream().map(future -> {
				try {
					return future.get();
				} catch (InterruptedException | ExecutionException e) {
					return null;
				}
			}).filter(result -> result != null).filter(result -> {
				try {
					return digitalSignatureUtil.verifySignature(result.getDigest(), result.getSignature(),
							variables.getPublicKeys().get(result.getI()));
				} catch (Exception e) {
					return false;
				}
			}).collect(Collectors.toList());
		}
		performanceService.logTaskEnd(10);
		return new ArrayList<>();
	}

	@Override
	public void processOptimisticCommitRequest(PrepareAndCommit prepare) {
		performanceService.logTaskStart();
		if (peerUtils.isByzantineServer()) {
			return;
		}
		prepare.setI(peerUtils.getServerPort());
		PrepareAndCommit commit = generateCommitMessage(prepare);
		if (!variables.getPrepare().contains(prepare)) {
			variables.getPrepare().add(prepare);
		}
		if (!variables.getCommitted().contains(commit)) {
			variables.getCommitted().add(commit);
			variables.getDataStore().add(variables.getDataStoreCounter() + " (" + commit.getMessage().getSender() + ","
					+ commit.getMessage().getReceiver() + "," + commit.getMessage().getAmount() + ")");
			variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
		}
		executeTransactionsUntil(commit);
		performanceService.logTaskEnd(1);
		return;
	}

	public List<PrepareAndCommit> PrePreparePhase(PrePrepareRequest ppr) {
		performanceService.logTaskStart();
		List<CompletableFuture<PrepareAndCommit>> futures = peerUtils.getPeersList().stream()
				.map(url -> CompletableFuture.supplyAsync(() -> {
					try {
						return restTemplate.postForObject("http://" + url + "/api/preprepare", ppr,
								PrepareAndCommit.class);
					} catch (Exception e) {
						return null;
					}
				}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();
		performanceService.logTaskEnd(10);
		return futures.stream().map(future -> {
			try {
				return future.get();
			} catch (InterruptedException | ExecutionException e) {
				return null;
			}
		}).filter(result -> result != null).filter(result -> {
			try {
				return digitalSignatureUtil.verifySignature(result.getDigest(), result.getSignature(),
						variables.getPublicKeys().get(result.getI()));
			} catch (Exception e) {
				return false;
			}
		}).collect(Collectors.toList());
	}

	@Override
	public void processCommitRequest(PrepareAndCommit commit) {
		performanceService.logTaskStart();
		variables.getLocks().remove(commit.getMessage().getSender());
		variables.getLocks().remove(commit.getMessage().getReceiver());
		if (peerUtils.isByzantineServer()) {
			return;
		}
		commit.setI(peerUtils.getServerPort());
		if (!variables.getCommitted().contains(commit)) {
			variables.getCommitted().add(commit);
		}
		executeTransactionsUntil(commit);
		performanceService.logTaskEnd(2);
		return;
	}

	@Override
	public void processCrossCommitRequest(PrepareAndCommit commit) {
		performanceService.logTaskStart();
		if (peerUtils.isByzantineServer()) {
			return;
		}
//		if ("from".equals(commit.getMessage().getGrp())) {
//			variables.getLocks().remove(commit.getMessage().getSender());
//		} else {
//			variables.getLocks().remove(commit.getMessage().getReceiver());
//		}
		commit.setI(peerUtils.getServerPort());
		if (!variables.getCommitted().contains(commit)) {
			variables.getCommitted().add(commit);
			variables.getDataStore()
					.add(variables.getDataStoreCounter() + " P " + " (" + commit.getMessage().getSender() + ","
							+ commit.getMessage().getReceiver() + "," + commit.getMessage().getAmount() + ")");
			variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
		}
		if (!peerUtils.quorumNotPresentAtReceiverSide(commit.getMessage())) {
			variables.setLastExecutedN(commit.getN());
		}
		performanceService.logTaskEnd(2);
		return;
	}

	@Override
	public PrepareAndCommit processPrepareRequest(PrepareAndCommit prepare) {
		performanceService.logTaskStart();
		if (peerUtils.isByzantineServer()) {
			return null;
		}
		prepare.setI(peerUtils.getServerPort());
		if (!variables.getPrepare().contains(prepare)) {
			variables.getPrepare().add(prepare);
		}
		variables.incrementCounter();
		PrepareAndCommit commit = generateCommitMessage(prepare);
		performanceService.logTaskEnd(1);
		return commit;
	}

	@Override
	public PrepareAndCommit processPrePrepareRequest(PrePrepareRequest ppq) {
		performanceService.logTaskStart();
		if (!variables.getPreprepare().contains(ppq)) {
			variables.getPreprepare().add(ppq);
		}
		if (ppq.getMessage().getGrp() == null) {
			variables.getLocks().add(ppq.getMessage().getSender());
			variables.getLocks().add(ppq.getMessage().getReceiver());
		}
		if ("from".equals(ppq.getMessage().getGrp())) {
			variables.getLocks().add(ppq.getMessage().getSender());
		}
		if ("to".equals(ppq.getMessage().getGrp())) {
			variables.getLocks().add(ppq.getMessage().getReceiver());
		}
		if (peerUtils.isByzantineServer()) {
			return null;
		}
		PrepareAndCommit prepare = generatePrepareMessage(ppq);
		performanceService.logTaskEnd(1);
		return prepare;
	}

	private PrepareAndCommit generateCommitMessage(PrepareAndCommit prepare) {
		performanceService.logTaskStart();
		PrepareAndCommit commit = new PrepareAndCommit();
		commit.setType("COMMITED");
		commit.setMessage(prepare.getMessage());
		commit.setV(prepare.getV());
		commit.setN(prepare.getN());
		commit.setI(peerUtils.getServerPort());
		try {
			commit.setDigest(hashUtils.hashWithSHA256(prepare.getMessage().toString()));
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Exception while generating digest");
		}
		try {
			commit.setSignature(digitalSignatureUtil.signMessage(prepare.getDigest(), variables.getPrivateKey()));
		} catch (Exception e) {
			System.out.println("Exception while signing digest..");
		}
		performanceService.logTaskEnd(1);
		return commit;
	}

	private PrepareAndCommit generatePrepareMessage(PrePrepareRequest ppr) {
		performanceService.logTaskStart();
		PrepareAndCommit prepare = new PrepareAndCommit();
		prepare.setType("PREPARED");
		prepare.setMessage(ppr.getMessage());
		prepare.setV(ppr.getV());
		prepare.setN(ppr.getN());
		prepare.setI(peerUtils.getServerPort());
		try {
			prepare.setDigest(hashUtils.hashWithSHA256(ppr.getMessage().toString()));
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Exception while generating digest");
		}
		try {
			prepare.setSignature(digitalSignatureUtil.signMessage(prepare.getDigest(), variables.getPrivateKey()));
		} catch (Exception e) {
			System.out.println("Exception while signing digest..");
		}
		performanceService.logTaskEnd(1);
		return prepare;
	}

	public PrePrepareRequest generatePrePrepareMessage(TransactionRequest transactionRequest) {
		performanceService.logTaskStart();
		PrePrepareRequest preprepareRequest = new PrePrepareRequest();
		preprepareRequest.setType("PRE-PREPARE");
		preprepareRequest.setV(variables.getView());
		preprepareRequest.setN(variables.getCounter());
		try {
			preprepareRequest.setDigest(hashUtils.hashWithSHA256(transactionRequest.getTransaction().toString()));
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Exception while generating digest...");
		}
		try {
			preprepareRequest.setSignature(
					digitalSignatureUtil.signMessage(preprepareRequest.getDigest(), variables.getPrivateKey()));
		} catch (Exception e) {
			System.out.println("Exception while signing digest...");
		}
		preprepareRequest.setMessage(transactionRequest.getTransaction());
		performanceService.logTaskEnd(1);
		return preprepareRequest;
	}

	@Override
	public List<PrepareAndCommit> getPrepareLog() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getPrepare();
	}

	@Override
	public List<PrepareAndCommit> getCommitLog() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getCommitted();
	}

	@Override
	public List<PrePrepareRequest> getPrePrepareLog() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getPreprepare();
	}

	private boolean verifyRequestSignature(TransactionRequest transactionRequest) {
		performanceService.logTaskStart();
		try {
			return digitalSignatureUtil.verifySignature(
					hashUtils.hashWithSHA256(transactionRequest.getTransaction().toString()),
					transactionRequest.getSignature(), variables.getPublicKeys().get(0));
		} catch (Exception e) {
			System.out.println("Error while verifying the signature from the client...");
		}
		performanceService.logTaskEnd(1);
		return false;
	}

	private Reply generateReplyMessage(PrepareAndCommit commit) {
		performanceService.logTaskStart();
		Reply reply = new Reply();
		reply.setType("EXECUTED");
		reply.setI(commit.getI());
		reply.setTimestamp(commit.getMessage().getTimestamp());
		reply.setV(commit.getV());
		reply.setN(commit.getN());
		performanceService.logTaskEnd(1);
		return reply;
	}

	private synchronized void executeTransactionsUntil(PrepareAndCommit commit) {
		performanceService.logTaskStart();
		int targetN = commit.getN();
		if (targetN > variables.getLastExecutedN()) {
			variables.getPendingTransactions().put(targetN, commit);
		}
		while (variables.getPendingTransactions().containsKey(variables.getLastExecutedN() + 1)) {
			int nextN = variables.getLastExecutedN() + 1;
			PrepareAndCommit nextCommit = variables.getPendingTransactions().get(nextN);
			if (localExecutedTransactions.contains(nextN)) {
				variables.getPendingTransactions().remove(nextN);
				variables.incrementLastExecutedN();
				continue;
			}
			localExecutedTransactions.add(nextN);
			Reply reply = generateReplyMessage(nextCommit);
			Account sender = accountRepository.findByAccountId(nextCommit.getMessage().getSender());
			Account receiver = accountRepository.findByAccountId(nextCommit.getMessage().getReceiver());
			if (sender != null && receiver != null && sender.getBalance() >= nextCommit.getMessage().getAmount()) {
				sender.setBalance(sender.getBalance() - nextCommit.getMessage().getAmount());
				receiver.setBalance(receiver.getBalance() + nextCommit.getMessage().getAmount());
				accountRepository.save(sender);
				accountRepository.save(receiver);
				variables.getExecuted().add(reply);
				variables.getDataStore().add(variables.getDataStoreCounter() + " (" + sender.getAccNo() + ","
						+ receiver.getAccNo() + "," + nextCommit.getMessage().getAmount() + ")");
				variables.setDataStoreCounter(variables.getDataStoreCounter() + 1);
			} else {
				variables.getExecuted().add(reply);
				System.out
						.println("Transaction N=" + nextN + " failed due to insufficient balance or invalid accounts.");
			}
			variables.getPendingTransactions().remove(nextN);
			variables.incrementLastExecutedN();
		}
		performanceService.logTaskEnd(1);
		localExecutedTransactions.clear();
	}

	private synchronized void executeTransaction(TransactionRequest transactionRequest) {
		performanceService.logTaskStart();
		if (peerUtils.isByzantineServer()) {
			return;
		}
		Transaction transaction = transactionRequest.getTransaction();
		PrepareAndCommit commit = getTransactionCommit(transaction);
		int targetN = commit.getN();
		System.out.println(commit.toString());
		if (targetN > variables.getLastExecutedN()) {
			variables.getPendingTransactions().put(targetN, commit);
		}
		while (variables.getPendingTransactions().containsKey(variables.getLastExecutedN() + 1)) {
			int nextN = variables.getLastExecutedN() + 1;
			PrepareAndCommit nextCommit = variables.getPendingTransactions().get(nextN);
			System.out.println("Processing transaction N: " + nextN);
			System.out.println("Current lastExecutedN: " + variables.getLastExecutedN());
			if (nextCommit == null) {
				System.out.println("Transaction N: " + nextN + " is incomplete or invalid. Skipping.");
				variables.incrementLastExecutedN();
				variables.getPendingTransactions().remove(nextN);
				continue;
			}
			if (localExecutedTransactions.contains(nextN)) {
				variables.getPendingTransactions().remove(nextN);
				variables.incrementLastExecutedN();
				continue;
			}
			localExecutedTransactions.add(nextN);
			Reply reply = generateReplyMessage(nextCommit);

			if ("from".equals(transaction.getGrp())) {
				Account sender = accountRepository.findByAccountId(transaction.getSender());
				sender.setBalance(sender.getBalance() - transaction.getAmount());
				accountRepository.save(sender);
			} else {
				Account receiver = accountRepository.findByAccountId(transaction.getReceiver());
				receiver.setBalance(receiver.getBalance() + transaction.getAmount());
				accountRepository.save(receiver);
			}
			variables.getExecuted().add(reply);
			variables.getPendingTransactions().remove(nextN);
			variables.incrementLastExecutedN();
		}
		performanceService.logTaskEnd(1);
	}

	private PrepareAndCommit getTransactionCommit(Transaction t) {
		if (variables.getCommitted().isEmpty()) {
			return null;
		}
		for (PrepareAndCommit commit : variables.getCommitted()) {
			Transaction transaction = commit.getMessage();
			if (Objects.equals(transaction.getSender(), t.getSender())
					&& Objects.equals(transaction.getReceiver(), t.getReceiver())
					&& transaction.getAmount() == t.getAmount() && transaction.getTimestamp() == t.getTimestamp()
					&& Objects.equals(transaction.getIsIntraShard(), t.getIsIntraShard())
					&& Objects.equals(transaction.getGrp(), t.getGrp()) && transaction.getServer() == t.getServer()) {
				return commit;
			}
		}
		return null;
	}

	@Override
	public List<Reply> getExecuted() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getExecuted();
	}

	@Override
	public void filter() {
		performanceService.logTaskStart();
		@SuppressWarnings("unused")
		List<CompletableFuture<Void>> futures = peerUtils.getAllServersList().stream()
				.map(url -> CompletableFuture.runAsync(() -> {
					try {
						restTemplate.postForLocation("http://" + url + "/api/f/pp", null);
					} catch (Exception e) {
					}
				}).orTimeout(timeoutInSeconds, TimeUnit.SECONDS)).toList();

		performanceService.logTaskEnd(7);
	}

	@Override
	public void filterPrePrepare() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		variables.FilterPrePrepareLog();
	}

	@Override
	public String getStatus(int sequenceNumber) {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getStatusBySequenceNumber(sequenceNumber);
	}

	@Override
	public CombinedLogs getCombinedLogs() {
		performanceService.logTaskStart();
		CombinedLogs combinedLogs = new CombinedLogs();
		combinedLogs.setPrePrepareLogs(getPrePrepareLog());
		combinedLogs.setPrepareLogs(getPrepareLog());
		combinedLogs.setCommitLogs(getCommitLog());
		combinedLogs.setExecutedLogs(getExecuted());

		performanceService.logTaskEnd(1);
		return combinedLogs;
	}

	@Override
	public List<Account> PrintDB() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return accountRepository.findAll();
	}

	@Override
	public int getBalance(long accountId) {
		return accountRepository.getBalanceByAccount(accountId);
	}

	private void retryQuorumTransactions() {
		List<PrepareAndCommit> replies = new ArrayList<>();
		if (peerUtils.quorumReachedOrNot()) {
			for (PrepareAndCommit prepare : variables.getPt()) {
				if (!peerUtils.isByzantineServer()) {
					variables.getPrepare().add(prepare);
					replies = PreparePhase(prepare, replies);
					PrepareAndCommit commit = generateCommitMessage(prepare);
					CommitPhase(commit, replies);
					variables.getLocks().remove(commit.getMessage().getSender());
					variables.getLocks().remove(commit.getMessage().getReceiver());
				}
			}
			variables.getPt().clear();
		}
		return;
	}

	@Override
	public List<String> getDataStore() {
		return variables.getDataStore();
	}

	private void reverseTransaction(PrePrepareRequest ppr) {
		Transaction transaction = variables.getWAL().get(ppr.getN());
		if ("from".equals(transaction.getGrp())) {
			Account sender = accountRepository.findByAccountId(transaction.getSender());
			sender.setBalance(sender.getBalance() + transaction.getAmount());
			accountRepository.save(sender);
		} else {
			Account receiver = accountRepository.findByAccountId(transaction.getReceiver());
			receiver.setBalance(receiver.getBalance() - transaction.getAmount());
			accountRepository.save(receiver);
		}
	}

	@Override
	public boolean processDoubleTransaction(TransactionRequest transactionRequest) {
		if (transactionRequest == null || transactionRequest.getTransaction() == null) {
			System.err.println("Invalid transaction request.");
			return false;
		}
		Transaction transaction = transactionRequest.getTransaction();
		Long sender = transaction.getSender();
		Long receiver1 = transaction.getReceiver();
		Long receiver2 = transaction.getReceiver2();
		int amount1 = transaction.getAmount();
		int amount2 = transaction.getAmount2();
		int totalAmount = amount1 + amount2;
		if (accountRepository.getBalanceByAccount(sender) < totalAmount) {
			System.err.println("Insufficient balance for cross-shard transaction.");
			return false;
		}
		Long timestamp = System.currentTimeMillis();
		TransactionRequest firstTransactionRequest = new TransactionRequest();
		Transaction firstTransaction = new Transaction();
		firstTransaction.setAmount(amount1);
		firstTransaction.setSender(sender);
		firstTransaction.setReceiver(receiver1);
		firstTransactionRequest.setTransaction(firstTransaction);
		Transaction secondTransaction = new Transaction();
		secondTransaction.setAmount(amount1);
		secondTransaction.setSender(sender);
		secondTransaction.setReceiver(receiver1);
		TransactionRequest secondTransactionRequest = new TransactionRequest();
		secondTransactionRequest.setTransaction(secondTransaction);

		boolean firstTransactionSuccess = false;
		boolean secondTransactionSuccess = false;
		try {
			processCrossShardTransaction(firstTransactionRequest);
			firstTransactionSuccess = true;
		} catch (Exception e) {
		}
		if (firstTransactionSuccess) {
			try {
				System.out.println("Processing second cross-shard transaction...");
				processCrossShardTransaction(secondTransactionRequest);
				secondTransactionSuccess = true;
			} catch (Exception e) {
			}
		}
		if (firstTransactionSuccess && secondTransactionSuccess) {
			System.out.print("success");
			return true;
		} else {
		}
		return false;
	}
}
