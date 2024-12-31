package com.example.two_phase_pbft.Services;

import java.security.PublicKey;

import com.example.two_phase_pbft.Models.ReceiveKey;


public interface SignatureService {

	boolean generateKeyPair();
	PublicKey getKey();
	String receiveKey(ReceiveKey receiveKey);
	void printPeerPublicKeys();

}
