package com.example.two_phase_pbft.Controllers;

import java.security.PublicKey;
import java.util.Base64;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.two_phase_pbft.Models.ReceiveKey;
import com.example.two_phase_pbft.Services.SignatureService;


@RestController
public class SignatureController {
	
	private final SignatureService signatureService; 
	
	public SignatureController(SignatureService signatureService) {
		super();
		this.signatureService = signatureService;
	}

	@PostMapping("/generate/keypair")
	public String generateKeyPair() {
		if(signatureService.generateKeyPair()) {
			return "Key Pair generated successfully";
		}
		return "There is an error while Generating Key Pair";
	}
	
	@GetMapping("/get/key")
	public String getKey() {
        PublicKey publicKey = signatureService.getKey();
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());

	}
	
	@PostMapping("/receive/key")
    public String receivePublicKey(@RequestBody ReceiveKey receiveKey) {
        return signatureService.receiveKey(receiveKey);
    }
	
	@GetMapping("/print/keys")
	public void getKeys(){
		signatureService.printPeerPublicKeys();
	}
}
	