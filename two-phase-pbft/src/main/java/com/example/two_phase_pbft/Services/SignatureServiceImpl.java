package com.example.two_phase_pbft.Services;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import org.springframework.stereotype.Service;

import com.example.two_phase_pbft.GlobalVariables.Variables;
import com.example.two_phase_pbft.Models.ReceiveKey;
import com.example.two_phase_pbft.Utils.KeyPairGeneratorUtils;

@Service
public class SignatureServiceImpl implements SignatureService {

	private final KeyPairGeneratorUtils keyPairGeneratorUtils;
	private final Variables variables;
	private final PerformanceService performanceService;

	public SignatureServiceImpl(KeyPairGeneratorUtils keyPairGeneratorUtils, Variables variables,
			PerformanceService performanceService) {
		super();
		this.keyPairGeneratorUtils = keyPairGeneratorUtils;
		this.variables = variables;
		this.performanceService = performanceService;
	}

	@Override
	public boolean generateKeyPair() {
		performanceService.logTaskStart();
		KeyPair keyPair;
		try {
			keyPair = keyPairGeneratorUtils.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			return false;
		}
		variables.setPrivateKey(keyPair.getPrivate());
		variables.setPublicKey(keyPair.getPublic());
		performanceService.logTaskEnd(1);
		return true;
	}

	@Override
	public PublicKey getKey() {
		performanceService.logTaskStart();
		performanceService.logTaskEnd(1);
		return variables.getPublicKey();
	}

	@Override
	public String receiveKey(ReceiveKey receiveKey) {
		performanceService.logTaskStart();
		try {
			PublicKey publicKey = keyPairGeneratorUtils.getPublicKeyFromString(receiveKey.getPublicKey());
			variables.getPublicKeys().put(receiveKey.getServer(), publicKey);
			return "Public key received and reconstructed successfully!";
		} catch (Exception e) {
			return "Error reconstructing the public key: " + e.getMessage();
		} finally {
			performanceService.logTaskEnd(1);
		}
	}

	@Override
	public void printPeerPublicKeys() {
		performanceService.logTaskStart();
		for (Integer key : variables.getPublicKeys().keySet()) {
			PublicKey publicKey = variables.getPublicKeys().get(key);
			System.out.println("Server : " + key + "\n" + " PublicKey: " + publicKey);
		}
		performanceService.logTaskEnd(1);
	}

}
