package com.example.two_phase_pbft.GlobalVariables;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.two_phase_pbft.Models.PTC;
import com.example.two_phase_pbft.Models.PrePrepareRequest;
import com.example.two_phase_pbft.Models.PrepareAndCommit;
import com.example.two_phase_pbft.Models.Reply;
import com.example.two_phase_pbft.Models.Transaction;

import lombok.Data;

@Data
@Component
public class Variables {

	@Value("${server.port}")
	private int serverPort;
	@Value("${application.clustersize}")
	private int clusterSize;
	@Value("${application.cluster}")
	private int clusterNo;
	@Value("${application.shardsize}")
	private int shardSize;
	@Value("${application.noofclusters}")
	private long noOfClusters;
	private PrivateKey privateKey;
	private PublicKey publicKey;
	private List<Integer> disconnectedServers = new ArrayList<>();
	private List<Integer> byzantineServers = new ArrayList<>();
	private List<Integer> primaryServers = new ArrayList<>();
	private HashMap<Integer, PublicKey> publicKeys = new HashMap<>();
	private int view = 1;
	private int checkpoint = 0;
	private List<PrePrepareRequest> preprepare = new ArrayList<>();
	private List<PrepareAndCommit> prepare = new ArrayList<>();
	private List<PrepareAndCommit> committed = new ArrayList<>();
	private List<Reply> executed = new ArrayList<>();
	private AtomicInteger counter = new AtomicInteger(0);
	private final ReentrantLock lock = new ReentrantLock();
	private final int faultsTolerated = 1;
	private AtomicInteger lastExecutedN = new AtomicInteger(0);
	private HashMap<Integer, PrepareAndCommit> pendingTransactions = new HashMap<>();
	private final int THRESHOLD = 3;
	private final int TOTAL_SHARES = 4;
	private final int CHECKPOINT_INTERVAL = 100;
	private Set<Long> locks=new HashSet<>();
    private NavigableMap<Long, Long> shardMap = new TreeMap<>();
    private List<String> dataStore = new ArrayList<>();
	private HashMap<Integer,Transaction> WAL = new HashMap<>(); 
	private int dataStoreCounter=1;
	List<PrepareAndCommit> pt=new ArrayList<>();
	List<PTC> ptc=new ArrayList<>();

    public void createShardMappings(){
       	int start = 1;
        for (int cluster = 1; cluster <= noOfClusters; cluster++) {
            int end = start + shardSize - 1;
            shardMap.put((long)end,(long)cluster);
            start = end + 1;
        }
    }
    
    public Long getCluster(Long accountId) {
    	return shardMap.ceilingEntry(accountId).getValue();
    }

	public void incrementCounter() {
		lock.lock();
		try {
			counter.incrementAndGet();
		}finally {
			lock.unlock();
		}
	}
	
	public int getCounter() {
		lock.lock();
		try {
			return counter.get();
		} finally {
			incrementCounter();
			lock.unlock();
		}
	}

	public void setCounter(int value) {
		lock.lock();
		try {
			counter.set(value);
		}finally {
			lock.unlock();
		}
	}
	
	public void setLastExecutedN(int value) {
		lock.lock();
		try {
			lastExecutedN.set(value);
		} finally {
			lock.unlock();
		}
	}

	public int getLastExecutedN() {
		lock.lock();
		try {
			return lastExecutedN.get();
		} finally {
			lock.unlock();
		}
	}

	public String getStatusBySequenceNumber(int n) {
		for (Reply reply : executed) {
			if (reply.getN() == n) {
				return "E";
			}
		}
		for (PrepareAndCommit commit : committed) {
			if (commit.getN() == n) {
				return "C";
			}
		}
		for (PrepareAndCommit prep : prepare) {
			if (prep.getN() == n && !byzantineServers.contains(prep.getI())) {
				return "P";
			}
		}
		for (PrePrepareRequest prePrep : preprepare) {
			if (prePrep.getN() == n) {
				return "PP";
			}
		}
		return "X";
	}

	public void FilterPrePrepareLog() {
		setPreprepare(preprepare.stream()
				.collect(Collectors.groupingBy(request -> request.getMessage().getSender(),
						Collectors.minBy(Comparator.comparingInt(PrePrepareRequest::getN))))
				.values().stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));
	}

	public void incrementLastExecutedN() {
		lock.lock();
		lastExecutedN.incrementAndGet();
		lock.unlock();
	}

	public int getClusterNo() {
		return clusterNo;
	}

	public int getClusterSize() {
		return clusterSize;
	}

	public boolean isDisConnectedServer() {
		return disconnectedServers.contains(serverPort);
	}

}
