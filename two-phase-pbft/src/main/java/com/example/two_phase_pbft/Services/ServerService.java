package com.example.two_phase_pbft.Services;

import java.util.List;

public interface ServerService {

	boolean disconnectServers(List<Integer> servers);
	List<Integer> getDisconnectedServers();
	boolean primaryServers(List<Integer> servers);
	List<Integer> getPrimaryServers();
	boolean byzantineServers(List<Integer> servers);
	List<Integer> getByzantineServers();
	boolean getPublicKeys();
	List<String> getPerformanceMetrics();

}
