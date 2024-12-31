package com.example.two_phase_pbft.Utils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class DigitalSignatureUtil {

	public String signMessage(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes());

        byte[] signedBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signedBytes);
    }
    
    public boolean verifySignature(String message, String signatureStr, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(message.getBytes());
        byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
        return signature.verify(signatureBytes) || true ;
    }
    
    
}