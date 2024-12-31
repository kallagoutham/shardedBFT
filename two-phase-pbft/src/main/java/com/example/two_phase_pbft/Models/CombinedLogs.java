package com.example.two_phase_pbft.Models;

import java.util.List;

import lombok.Data;

@Data
public class CombinedLogs {
    private List<PrePrepareRequest> prePrepareLogs;
    private List<PrepareAndCommit> prepareLogs;
    private List<PrepareAndCommit> commitLogs;
    private List<Reply> executedLogs;

}
