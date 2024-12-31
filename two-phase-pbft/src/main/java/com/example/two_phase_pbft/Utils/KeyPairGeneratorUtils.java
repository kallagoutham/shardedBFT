package com.example.two_phase_pbft.Utils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class KeyPairGeneratorUtils {
	
	    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
	        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
	        keyGen.initialize(2048);
	        return keyGen.generateKeyPair();
	    }

		public PublicKey getPublicKeyFromString(String publicKey) {
			try {
	            byte[] keyBytes = Base64.getDecoder().decode(publicKey);
	            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
	            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
	            return keyFactory.generatePublic(spec);
	        } catch (Exception e) {
	            throw new RuntimeException("Error reconstructing the public key", e);
	        }
		}

}
