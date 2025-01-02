# Byzantine Fault-Tolerant Distributed Transaction Processing

## Project Overview
This project involves implementing a Byzantine fault-tolerant distributed transaction processing system, simulating a permissioned blockchain system. The system supports intra-shard and cross-shard transactions for a simple banking application. Intra-shard transactions use the Linear PBFT protocol, while cross-shard transactions leverage the Two-Phase Commit (2PC) protocol.

### Objectives
- Implement Byzantine fault-tolerant consensus for intra-shard transactions.
- Enable cross-shard transactions with fault-tolerant coordination.
- Ensure system resilience and security in untrustworthy environments.
- Optimize transaction processing for performance.

## Features and Functionality

### Core Features
1. **Clustered Shard Management**:
   - Data is partitioned into three shards: D1, D2, and D3.
   - Each shard is replicated across four servers in a cluster.
2. **Intra-Shard Transactions**:
   - Transactions within a shard are processed using the Linear PBFT protocol.
   - Ensures consensus while acquiring locks on accessed records.
3. **Cross-Shard Transactions**:
   - Transactions spanning multiple shards use the 2PC protocol.
   - The coordinator cluster manages transaction preparation and commit phases.
4. **Fault Tolerance**:
   - Resilient against Byzantine failures with up to one faulty server per cluster.
   - Prevents replay attacks using client-request timestamps.
5. **Security**:
   - Lock mechanisms prevent double-spending.
   - Consensus ensures data integrity.

### Additional Functions
- **PrintBalance**: Retrieves and prints the balance of a specified client across all servers.
- **PrintDatastore**: Displays the committed transaction log for each server.
- **Performance Metrics**: Measures throughput (transactions per second) and latency.

## System Description

### Architecture
- **Clusters and Shards**:
  - Three clusters, each managing a shard of 1000 data items.
  - Shard mapping:
    - C1: [1, ..., 1000]
    - C2: [1001, ..., 2000]
    - C3: [2001, ..., 3000]
- **Transaction Types**:
  - **Intra-Shard Transactions**: Access data within a single shard.
  - **Cross-Shard Transactions**: Access data across multiple shards.

### Intra-Shard Transactions
- Processed using the Linear PBFT protocol with:
  - Primary server coordinating consensus.
  - Lock acquisition on accessed records.
  - View-change mechanism to replace slow leaders.

### Cross-Shard Transactions
- Handled using the 2PC protocol with:
  1. **Coordinator Cluster**:
     - Initiates consensus on the transaction using Linear PBFT.
     - Executes the transaction and updates WAL.
  2. **Participant Cluster**:
     - Validates and locks accessed records.
     - Achieves consensus for transaction preparation and sends prepared or abort messages.
  3. **Commit Phase**:
     - Coordinator and participant clusters commit or abort based on consensus.
     - Locks are released, and WAL entries are updated.

## Implementation Details

### Prescribed Conditions
- Dataset: 3000 data items, each initialized with 10 units.
- Key-value database is required for data storage (file-based storage is not allowed).

### Functions
1. **PrintBalance(Client ID)**: Displays the balance of the specified client.
2. **PrintDatastore()**: Outputs committed transactions for each server.
3. **Performance()**: Reports throughput and latency metrics.

### Communication Among Servers
  I have used TCP/HTTP for communication among servers. I am providing postman collection file of API endpoints that I developed for the communication among Paxos Servers under lab4_resources.

### Input Format
- Input files should be CSV files with columns:
  1. **Set Number**: Identifier for the test case.
  2. **Transactions**: List of transactions `(Sender, Receiver, Amount)`.
  3. **Live Servers**: Active servers for the test case.
  4. **Byzantine Servers**: Servers exhibiting Byzantine behavior.

#### Example Input
```csv
Set Number, Transactions, Live Servers, Byzantine Servers
1, (21, 700, 2); (100, 501, 8), [S1, S2, S4, S5, S6, S8, S9, S10, S11, S12], [S9]
2, (702, 1301, 2); (1301, 1302, 3), [S1, S2, S3, S5, S6, S8, S9], [S3, S6, S8]
```

## Bonus Features
- [x] **Multi-Shard Transactions**:
   - Support atomic transactions involving all three shards.
   - Modify input to include transfers to multiple participants.
- [x] **SmallBank Benchmark**:
   - Implement and evaluate system performance using the SmallBank benchmark.
   - Simulates a real-world banking application workload.

## Submission Instructions

### Repository Setup
1. Clone the GitHub repository:
   ```bash
   https://github.com/kallagoutham/2pc-pbft.git
   ```
2. Compile Client.java and run Client.class file in lab2_resources. Then a list of options will be appeared select appropriate option to run and evaluate results.

## References
- Linear PBFT protocol documentation.
- Two-Phase Commit protocol resources.
- [GitHub Setup Instructions](https://docs.github.com/en/get-started/getting-started-with-git/set-up-git).

👨‍💻 **Kalla Goutham**    
🌐 [Website](https://gouthamkalla.netlify.app/) | [LinkedIn](https://www.linkedin.com/in/goutham-kalla-3b6133112/) | [GitHub](https://github.com/kallagoutham)  
✉️ Reach me at: kallagoutham33@gmail.com
