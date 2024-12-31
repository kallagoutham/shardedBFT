package com.example.two_phase_pbft.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.two_phase_pbft.Models.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

	@Query("Select a from Account a where a.accountId=?1")
	Account findByAccountId(Long id);

	@Query("SELECT a.balance FROM Account a WHERE a.accountId = ?1")
	int getBalanceByAccount(Long sender);

}
