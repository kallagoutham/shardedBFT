package com.example.two_phase_pbft.Utils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.two_phase_pbft.GlobalVariables.Variables;
import com.example.two_phase_pbft.Models.Transaction;

@Component
public class PeerUtils {
	
	@Value("${server.port}")
	private int serverPort;
	private final Variables variables;

	public PeerUtils(Variables variables) {
		super();
		this.variables = variables;
	}
	
	public List<String> getPeersOf(Long accountId) {
	    List<String> peers = new ArrayList<>();

	    int clusterNumber = Math.toIntExact(variables.getCluster(accountId));
	    int startPort = 8080 + (clusterNumber - 1) * variables.getClusterSize();
	    int endPort = 8080 + clusterNumber * variables.getClusterSize();
	    for (int i = startPort; i < endPort; i++) {
	        if (serverPort != i) {
	            peers.add("localhost:" + i);
	        }
	    }
	    return peers;
	}


	public List<String> getPeersList() {
		List<String> peers = new ArrayList<>();
		for (int i = (8080 + (variables.getClusterNo() - 1) * variables.getClusterSize()); i < (8080
				+ (variables.getClusterNo()) * variables.getClusterSize()); ++i) {
			if (serverPort != i && !variables.getDisconnectedServers().contains(i)) {
				peers.add("localhost:" + i);
			}
		}
		return peers;
	}

	public List<String> getPeersListIncludingDisconnected() {
		List<String> peers = new ArrayList<>();
		for (int i = (8080 + (variables.getClusterNo() - 1) * variables.getClusterSize()); i < (8080
				+ (variables.getClusterNo()) * variables.getClusterSize()); ++i) {
			if (serverPort != i) {
				peers.add("localhost:" + i);
			}
		}
		return peers;
	}

	public List<String> getAllServersList() {
		List<String> peers = new ArrayList<>();
		for (int i = (8080 + (variables.getClusterNo() - 1) * variables.getClusterSize()); i < (8080
				+ (variables.getClusterNo()) * variables.getClusterSize()); ++i) {
			if (!variables.getDisconnectedServers().contains(i)) {
				peers.add("localhost:" + i);
			}
		}
		return peers;
	}

	public List<String> getAllServersListIncludingDisconnected() {
		List<String> peers = new ArrayList<>();
		for (int i = (8080 + (variables.getClusterNo() - 1) * variables.getClusterSize()); i < (8080
				+ (variables.getClusterNo()) * variables.getClusterSize()); ++i) {
			peers.add("localhost:" + i);
		}
		return peers;

	}

	public boolean isDisonnected() {
		if (variables.getDisconnectedServers().contains(serverPort)) {
			return true;
		}
		return false;
	}
	
	public int getServerPort() {
		return serverPort;
	}
	
	public boolean isByzantineServer() {
		return variables.getByzantineServers().contains(serverPort);
	}

	public boolean isPrimaryServerForCluster() {
		return serverPort == variables.getPrimaryServers().get(variables.getClusterNo()-1);
	}

	public boolean quorumReachedOrNot() {
		int nos=0;
		for (int i = (8080 + (variables.getClusterNo() - 1) * variables.getClusterSize()); i < (8080
				+ (variables.getClusterNo()) * variables.getClusterSize()); ++i) {
			if(!variables.getDisconnectedServers().contains(i) && !variables.getByzantineServers().contains(i)) {
				nos++;
			}
		}
		return nos>=variables.getFaultsTolerated()*2 +1;
	}

	public boolean quorumNotPresentAtReceiverSide(Transaction message) {
		int nos = 0;
		int clusterNumber = Math.toIntExact(variables.getCluster(message.getReceiver()));
		int startPort = 8080 + (clusterNumber - 1) * variables.getClusterSize();
		int endPort = 8080 + clusterNumber * variables.getClusterSize();
		for (int i = startPort; i < endPort; i++) {
			if (!variables.getByzantineServers().contains(i) && !variables.getDisconnectedServers().contains(i)) {
				nos++;
			}
		}
		return nos >= variables.getFaultsTolerated() * 2 + 1;
	}
	
}
