package com.example.two_phase_pbft.Models;

import java.util.Objects;

import lombok.Data;

@Data
public class Reply {
	
	private String type;
	private int V;
    private long timestamp;
    private int i;
    private int n;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reply reply = (Reply) o;
        return V == reply.V &&
               timestamp == reply.timestamp &&
               i == reply.i &&
               n == reply.n &&
               Objects.equals(type, reply.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, V, timestamp, i, n);
    }

   
}
