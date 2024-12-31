package com.example.two_phase_pbft.Models;

import java.util.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long transactionId;
    private Long sender;
    private Long receiver;
    private int amount;
    private Long receiver2;
    private int amount2;
    private long timestamp;
    private String isIntraShard;
    private String grp;
    private int server;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return amount == that.amount &&
               timestamp == that.timestamp &&
               Objects.equals(sender, that.sender) &&
               Objects.equals(receiver, that.receiver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, receiver, amount, timestamp);
    }
    
}