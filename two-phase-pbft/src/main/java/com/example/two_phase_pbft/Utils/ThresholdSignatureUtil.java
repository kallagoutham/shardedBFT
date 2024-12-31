package com.example.two_phase_pbft.Utils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.example.two_phase_pbft.GlobalVariables.Variables;
import com.example.two_phase_pbft.Models.PrepareAndCommit;

@Component
public class ThresholdSignatureUtil {

    private final DigitalSignatureUtil digitalSignatureUtil;
    private final Variables variables;
    private final HashUtils hashUtils;

	public ThresholdSignatureUtil(DigitalSignatureUtil digitalSignatureUtil, Variables variables, HashUtils hashUtils) {
		super();
		this.digitalSignatureUtil = digitalSignatureUtil;
		this.variables = variables;
		this.hashUtils = hashUtils;
	}

	public boolean verifyThresholdSignatures(List<PrepareAndCommit> replies, int threshold) {
        AtomicInteger validCount = new AtomicInteger(0);
        return replies.stream()
                .limit(variables.getPublicKeys().size())
                .anyMatch(reply -> {
                    try {
						if (digitalSignatureUtil.verifySignature(hashUtils.hashWithSHA256(reply.getMessage().toString()) , reply.getSignature(), variables.getPublicKeys().get(reply.getI()))) {
						    return validCount.incrementAndGet() >= threshold;
						}
					} catch (Exception e) {
						System.out.println("Error in TSS signature...");
					}
                    return false;
                });
    }
}
