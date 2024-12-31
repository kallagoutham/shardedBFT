package com.example.two_phase_pbft.Services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.springframework.stereotype.Service;

import com.example.two_phase_pbft.GlobalVariables.Variables;
import com.example.two_phase_pbft.Utils.KeyPairGeneratorUtils;
import com.example.two_phase_pbft.Utils.PeerUtils;

@Service
public class ServerServiceImpl implements ServerService{
	
	private final PerformanceService performanceService;
	private final Variables variables;
	private final PeerUtils peerUtils;
	private final KeyPairGeneratorUtils keyPairGeneratorUtils;
	CountDownLatch latch = new CountDownLatch(4);

	public ServerServiceImpl(PerformanceService performanceService, Variables variables, PeerUtils peerUtils,
			KeyPairGeneratorUtils keyPairGeneratorUtils) {
		super();
		this.performanceService = performanceService;
		this.variables = variables;
		this.peerUtils = peerUtils;
		this.keyPairGeneratorUtils = keyPairGeneratorUtils;
	}

	@Override
	public boolean disconnectServers(List<Integer> servers) {
		performanceService.logTaskStart();
		variables.setDisconnectedServers(servers);
		performanceService.logTaskEnd(1);
		return true;
	}

	@Override
	public List<Integer> getDisconnectedServers() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getDisconnectedServers();
	}

	@Override
	public boolean primaryServers(List<Integer> servers) {
		performanceService.logTaskStart();
		variables.setPrimaryServers(servers);
		performanceService.logTaskEnd(1);
		return true;
	}

	@Override
	public List<Integer> getPrimaryServers() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getPrimaryServers();
	}


	@Override
	public boolean byzantineServers(List<Integer> servers) {
		performanceService.logTaskStart();
		variables.setByzantineServers(servers);
		performanceService.logTaskEnd(1);
		return true;
	}

	@Override
	public List<Integer> getByzantineServers() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getByzantineServers();
	}
	
	@Override
	public boolean getPublicKeys() {
		performanceService.logTaskStart();
		List<String> peers = peerUtils.getPeersListIncludingDisconnected();
		latch = new CountDownLatch(peers.size());
		for (String peer : peers) {
			new Thread(() -> {
				try {
					URL url = new URL("http://" + peer + "/api/get/key");
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setRequestProperty("Content-Type", "application/json; utf-8");
					int responseCode = conn.getResponseCode();
					if (responseCode == 200) {
						BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
						String inputLine;
						StringBuilder response = new StringBuilder();
						while ((inputLine = in.readLine()) != null) {
							response.append(inputLine);
						}
						in.close();
						PublicKey publicKey = keyPairGeneratorUtils.getPublicKeyFromString(response.toString());
						variables.getPublicKeys().put(Integer.parseInt(peer.substring(10)), publicKey);
					}
				} catch (Exception e) {
					System.out.println("The server crashed/not-running : " + e.getMessage());
				} finally {
					latch.countDown();
				}
			}).start();
		}
		try {
			latch.await();
		} catch (InterruptedException e) {
			return false;
		}
		performanceService.logTaskEnd(7);
		return true;
	}

	@Override
	public List<String> getPerformanceMetrics() {
		System.out.println(performanceService.printPerformance().toString());
		return performanceService.printPerformance();
	}

}
