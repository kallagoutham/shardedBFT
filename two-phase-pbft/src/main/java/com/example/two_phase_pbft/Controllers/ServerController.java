package com.example.two_phase_pbft.Controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.two_phase_pbft.Services.PerformanceService;
import com.example.two_phase_pbft.Services.ServerService;

@RestController
public class ServerController {
	
	private final ServerService serverService;
	private final PerformanceService performanceService;

	public ServerController(ServerService serverService, PerformanceService performanceService) {
		super();
		this.serverService = serverService;
		this.performanceService = performanceService;
	}

	@PostMapping("/servers/disconnect")
	public ResponseEntity<String> disconnectServers(@RequestBody List<Integer> servers) {
		if (serverService.disconnectServers(servers)) {
			return ResponseEntity.status(200).body("Servers disconnected successfully");
		}
		return ResponseEntity.status(401).body("Unable to disconnect servers");
	}

	@GetMapping("/servers/disconnected")
	public List<Integer> getDisconnectedServers() {
		return serverService.getDisconnectedServers();
	}
	
	@PostMapping("/servers/primary")
	public ResponseEntity<String> primaryServers(@RequestBody List<Integer> servers){
		if(serverService.primaryServers(servers)){
			return ResponseEntity.status(200).body("Primary Servers saved successfully");
		}
		return ResponseEntity.status(200).body("Unable to save primary servers");
	}
	
	@GetMapping("/servers/primary")
	public List<Integer> getPrimaryServers() {
		return serverService.getPrimaryServers();
	}
	
	@PostMapping("/servers/byzantine")
	public ResponseEntity<String> byzantineServers(@RequestBody List<Integer> servers){
		if(serverService.byzantineServers(servers)){
			return ResponseEntity.status(200).body("Primary Servers saved successfully");
		}
		return ResponseEntity.status(200).body("Unable to save primary servers");
	}
	
	@GetMapping("/servers/byzantine")
	public List<Integer> getByazantineServers() {
		return serverService.getByzantineServers();
	}

	@GetMapping("/servers/publickeys")
	public ResponseEntity<String> getPublicKeysFromPeers(){
		if(serverService.getPublicKeys()) {
			return ResponseEntity.status(200).body("Public Keys Fetch Successful.");
		}
		return ResponseEntity.status(401).body("Unable to fetch public keys from peers.");
	}
	
	@GetMapping("/performance")
	public List<String> performanceMetrics(){
		return serverService.getPerformanceMetrics();
	}
	
}
