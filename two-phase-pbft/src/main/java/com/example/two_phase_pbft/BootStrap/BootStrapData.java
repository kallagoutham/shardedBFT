package com.example.two_phase_pbft.BootStrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.two_phase_pbft.GlobalVariables.Variables;
import com.example.two_phase_pbft.Models.Account;
import com.example.two_phase_pbft.Repository.AccountRepository;
import com.example.two_phase_pbft.Services.SignatureService;


@Component
public class BootStrapData implements CommandLineRunner {

	@Value("${application.cluster}")
	private long clusterNo;
	@Value("${application.shardsize}")
	private long shardSize;
	private final AccountRepository accountRepository;
	private final SignatureService signatureService;
	private final Variables variables;

	public BootStrapData(AccountRepository accountRepository, SignatureService signatureService, Variables variables) {
		super();
		this.accountRepository = accountRepository;
		this.signatureService = signatureService;
		this.variables = variables;
	}

	@Override
	public void run(String... args) throws Exception {
		for(long i=(((clusterNo-1)*shardSize) + 1);i<=(clusterNo*shardSize);i++) {
			Account account = new Account();
			account.setAccountId(i);
			account.setAccNo(i);
			account.setBalance(10);
			accountRepository.save(account);
		}
		signatureService.generateKeyPair();
		variables.createShardMappings();
	}

}
