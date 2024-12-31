
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

class TransactionData {

    List<String> transactions;
    String disconnectedServers;
    List<String> byzantineServers;
    List<String> primaryServers;

    public TransactionData() {
        transactions = new ArrayList<>();
        disconnectedServers = "";
        byzantineServers = new ArrayList<>();
        primaryServers = new ArrayList<>();
    }

}

public class Client {

    private static NavigableMap<Integer, Integer> shardMap;
    private static List<String> servers = new ArrayList<>();
    private static int shardSize = 1000;
    private static Map<String, Integer> ServerPortMap = new HashMap<>();
    private static final Queue<Runnable> commitQueue = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();
    private static KeyPair keyPair;

    static {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyPair = keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Error generating RSA key pair.");
        }
    }

    public static PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public static PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public static String signMessage(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes());
        byte[] signedBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signedBytes);
    }
    
    public static boolean verifySignature(String message, String signatureStr, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(message.getBytes());
        byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
        return signature.verify(signatureBytes);
    }

    public static String hashWithSHA256(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    public static void main(String[] args) {
        String csvFile = "lab4_test_cases_crossshard.csv";
        Scanner scanner = new Scanner(System.in);
        int noOfClusters = 3;
        int clusterSize = 4;
        List<String> allLines = new ArrayList<>();
        String line;
        String currentSet = null;
        Map<Integer, TransactionData> transactions = new HashMap<>();
        List<String> transactionList = new ArrayList<>();
        String disconnectedServers = "";
        List<String> byzantineServersList = new ArrayList<>();
        List<String> primaryServersList = new ArrayList<>();
        shardMap = createShardMappings(noOfClusters, shardSize);
        servers = createAllServers(noOfClusters, clusterSize);

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while ((line = br.readLine()) != null) {
                allLines.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading CSV file.");
            return;
        }

        for (int currentLineIndex = 0; currentLineIndex < allLines.size(); currentLineIndex++) {
            line = allLines.get(currentLineIndex).trim();
            String[] columns = line.split(",", 3);
            if (!columns[0].trim().isEmpty()) {
                if (currentSet != null) {
                    TransactionData transactionData = new TransactionData();
                    transactionData.transactions = new ArrayList<>(transactionList);
                    transactionData.disconnectedServers = disconnectedServers;
                    transactionData.byzantineServers = new ArrayList<>(byzantineServersList);
                    transactionData.primaryServers = new ArrayList<>(primaryServersList);
                    transactions.put(Integer.parseInt(currentSet), transactionData);
                    transactionList.clear();
                    disconnectedServers = "";
                    byzantineServersList.clear();
                    primaryServersList.clear();
                }
                currentSet = columns[0].trim();
            }
            if (columns.length > 1 && !columns[1].trim().isEmpty()) {
                int startIndex = line.indexOf('(');
                int endIndex = line.indexOf(')');
                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                    String extractedTransaction = line.substring(startIndex + 1, endIndex);
                    transactionList.add(extractedTransaction);
                }
                int start = line.indexOf("[");
                int end = line.indexOf("]");
                if (start != -1 && end != -1 && start < end) {
                    disconnectedServers = getDisconnectedServers(line.substring(start + 1, end).replaceAll("\\s+", ""), noOfClusters, clusterSize);
                }
                start = line.indexOf("[", end + 1);
                end = line.indexOf("]", start + 1);
                if (start != -1 && end != -1 && start < end) {
                    primaryServersList = new ArrayList<>(Arrays.asList(line.substring(start + 1, end).replaceAll("\\s+", "").split(",")));
                }
                start = line.indexOf("[", end + 1);
                end = line.indexOf("]", start + 1);
                if (start != -1 && end != -1 && start < end) {
                    byzantineServersList = new ArrayList<>(Arrays.asList(line.substring(start + 1, end).replaceAll("\\s+", "").split(",")));
                }
            }
            if (currentLineIndex == allLines.size() - 1 && currentSet != null) {
                TransactionData transactionData = new TransactionData();
                transactionData.transactions = new ArrayList<>(transactionList);
                transactionData.disconnectedServers = disconnectedServers;
                transactionData.byzantineServers = new ArrayList<>(byzantineServersList);
                transactionData.primaryServers = new ArrayList<>(primaryServersList);
                transactions.put(Integer.parseInt(currentSet), transactionData);
            }
        }
        while (true) {
            System.out.println("\nChoose an option:");
            System.out.println("0: Launch Servers");
            System.out.println("1: Process Transactions");
            System.out.println("2: Print Balance");
            System.out.println("3: Print DataStore");
            System.out.println("4: Print Performance Metrics");
            System.out.println("5: Print Shard Map");
            System.out.println("6: Exit");
            System.out.println("7: Exchange Keys");
            System.out.println("8: Generate Workload");
            System.out.print("Enter your choice: ");
            int choice = scanner.nextInt();
            switch (choice) {
                case 0:
                    LaunchServers(noOfClusters, clusterSize);
                    break;
                case 1:
                    System.out.println("Enter the set of the transaction to process : ");
                    Integer set = scanner.nextInt();
                    processTransactions(transactions.get(set),clusterSize);
                    break;
                case 2:
                    System.out.println("Enter the cluster to print balance (ex: 1302): ");
                    String client = scanner.next();
                    printBalance(client,clusterSize);
                    break;
                case 3:
                    printDataStore(noOfClusters*clusterSize);
                    break;
                case 4:
                    System.out.println("Enter the server to print performance metrics (ex: S1): ");
                    String server = scanner.next();
                    printPerformanceMetrics(server);
                    break;
                case 5:
                    printShardMap(shardMap);
                    break;
                case 6:
                    System.exit(0);
                    break;
                case 7:
                    exchangeKeys();
                    break;
                case 8:
                    generateWorkload(50, 100, 3, 4);
                default:
                    System.out.println("Invalid choice! Please try again.");
            }
        }
    }

    private static void processTransactions(TransactionData transactionData,int clusterSize) {
        List<String> transactions = transactionData.transactions;
        List<String> primaryServers = transactionData.primaryServers;
        disconnectServersRequest(transactionData.disconnectedServers);
        System.out.println("Disconnected servers: " + transactionData.disconnectedServers);
        sendPrimaryServersRequest(primaryServers);
        System.out.println("Primary servers: " + primaryServers);
        sendByzantineServersRequest(transactionData.byzantineServers);
        System.out.println("Byzantine servers: " + transactionData.byzantineServers);
        for (String transaction : transactions) {
            if (isIntraShardTransaction(transaction)) {
                int cluster = getCluster(Integer.parseInt(transaction.split(",")[0].trim()));
                processIntraShardTransaction(transaction,cluster,primaryServers);
            } else {
                int fromCluster = getCluster(Integer.parseInt(transaction.split(",")[0].trim()));
                int toCluster = getCluster(Integer.parseInt(transaction.split(",")[1].trim()));
                processCrossShardTransaction(transaction,fromCluster,toCluster,primaryServers,clusterSize);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        while (!commitQueue.isEmpty()) {
            Runnable commitTask = commitQueue.poll();
            commitTask.run();
        }
    }
    
    public static void processCrossShardTransaction(String transaction, int fromCluster, int toCluster,List<String> primaryServers, int clusterSize) {
        ConcurrentHashMap<String, String> responseMap = new ConcurrentHashMap<>();
        try {
            String[] transactionParts = transaction.split(",");
            String sender = transactionParts[0].trim();
            String receiver = transactionParts[1].trim();
            String amount = transactionParts[2].trim();
            long timestamp = System.currentTimeMillis();
            String jsonInputString = "{" +
                "\"type\": \"transfer\"," +
                "\"transaction\": {" +
                "\"sender\": " + transaction.split(",")[0].trim() + "," +
                "\"receiver\": " + transaction.split(",")[1].trim() + "," +
                "\"amount\": " + transaction.split(",")[2].trim() + "," +
                "\"isIntraShard\": false" + "," +
                "\"timestamp\": " + timestamp+","+
                "\"grp\":\"from\"" +
                "}," +
                "\"timestamp\": " + timestamp+ "," +
                "\"signature\": \"" + signMessage(transaction, getPrivateKey()) + "\"" + 
                "}";
            sendTransaction(jsonInputString, fromCluster, primaryServers, responseMap, "/api/bank/cross/transaction");
        } catch (Exception e) {
            System.err.println("Error while processing cross-shard transaction.");
        }
    }

    private static void addCommitToQueue(ConcurrentHashMap<String, String> responseMap, String sender, String receiver,String amount, long timestamp, int fromCluster, int toCluster,List<String> byzantineServers, int clusterSize) {
        Runnable commitTask = () -> {
            if (responseMap.get("fromCluster").contains("success") && responseMap.get("toCluster").contains("success")) {
                System.out.println("Transaction succeeded: " + sender + " -> " + receiver + " : " + amount + " | Committing...");
                sendCommitToAllServers("from", sender, receiver, amount, timestamp, fromCluster, byzantineServers, "/api/paxos/cross/commit", clusterSize, "COMMIT");
                sendCommitToAllServers("to", sender, receiver, amount, timestamp, toCluster, byzantineServers, "/api/paxos/cross/commit", clusterSize, "COMMIT");
            } else {
                System.out.println("Transaction failed: " + sender + " -> " + receiver + " : " + amount + " | Aborting...");
                sendCommitToAllServers("from", sender, receiver, amount, timestamp, fromCluster, byzantineServers, "/api/paxos/cross/commit", clusterSize, "ABORT");
                sendCommitToAllServers("to", sender, receiver, amount, timestamp, toCluster, byzantineServers, "/api/paxos/cross/commit", clusterSize, "ABORT");
            }
        };
        commitQueue.add(commitTask);
    }

    private static void sendCommitToAllServers(String group, String sender, String receiver, String amount, long timestamp, int cluster,List<String> servers, String endpoint, int clusterSize, String message) {
        for (int i = (8080 + (cluster - 1) * clusterSize); i < (8080 + cluster * clusterSize); i++) {
            try {
                String jsonInputString = String.format("{\"sender\":\"%s\",\"receiver\":\"%s\",\"amount\":%s,\"timestamp\":%d,\"isIntraShard\":false,\"grp\":\"%s\"}", sender, receiver, amount, timestamp, group);
                String urlString = "http://localhost:" + i + endpoint + "?message=" + message;
                String response = sendHttpRequest(urlString, jsonInputString);
            } catch (Exception e) {
                System.err.println("Error while sending commit/abort to server " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static CompletableFuture<Void> sendTransaction(String jsonInputString, int cluster, List<String> primaryServers, ConcurrentHashMap<String, String> responseMap, String endpoint) {
        return CompletableFuture.runAsync(() -> {
            try {
                String urlString = "http://localhost:" + ServerPortMap.get(primaryServers.get(cluster - 1)) + endpoint;
                String response = sendHttpRequest(urlString, jsonInputString);
            } catch (Exception e) {
            }
        });
    }

    private static String sendHttpRequest(String urlString, String jsonInputString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonInputString.getBytes("utf-8"));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } else {
            return "Failed: " + conn.getResponseMessage();
        }
    }

    private static void processIntraShardTransaction(String transaction,int cluster,List<String> byzantineServers) {
        long timestamp= System.currentTimeMillis();
        try {
            String jsonInputString = "{" +
                "\"type\": \"transfer\"," +
                "\"transaction\": {" +
                "\"sender\": " + transaction.split(",")[0].trim() + "," +
                "\"receiver\": " + transaction.split(",")[1].trim() + "," +
                "\"amount\": " + transaction.split(",")[2].trim() + "," +
                "\"timestamp\": " + timestamp+"," +
                "\"isIntraShard\": true" +
                "}," +
                "\"timestamp\": " + timestamp+ "," +
                "\"signature\": \"" + signMessage(transaction, getPrivateKey()) + "\"" +
                "}";

            URL url = new URL("http://localhost:"+ ServerPortMap.get(byzantineServers.get(cluster-1)) +"/api/bank/transaction");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
            } else {
                System.out.println("Failed to process transaction. Response: " + conn.getResponseMessage());
            }

        } catch (Exception e) {
            System.out.println("Error while sending transaction request.");
            e.printStackTrace();
        }
    }

    private static void disconnectServersRequest(String disconnectedServers) {
        List<Integer> server_ports = new ArrayList<>();
        for (String server : disconnectedServers.split(",")) {
            Integer port = ServerPortMap.get(server);
            if (port != null) {
                server_ports.add(port);
            }        
        }

        for (String peer : servers) {
            try {
                URL url = new URL("http://" + peer + "/api/servers/disconnect");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                String jsonInputString = server_ports.toString();
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    System.out.println("Error response from server: " + conn.getResponseMessage());
                }

            } catch (Exception e) {
                System.out.println("Error while disconnecting servers on peer: " + peer);
            }
        }
    }

    private static void sendPrimaryServersRequest(List<String> byzantineServers) {
        List<Integer> primaryServersPorts = new ArrayList<>();
        for (String server : byzantineServers) {
            primaryServersPorts.add(ServerPortMap.get(server));
        }

        for (String peer : servers) { 
            try {
                URL url = new URL("http://" + peer + "/api/servers/primary");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = primaryServersPorts.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                } else {
                    System.out.println("Error response from server: " + conn.getResponseMessage());
                }

            } catch (Exception e) {
                System.out.println("Error while sending primary servers to peer: " + peer);
            }
        }
    }

    private static void sendByzantineServersRequest(List<String> byzantineServers) {
        List<Integer> byzantineServersPorts = new ArrayList<>();
        for (String server : byzantineServers) {
            byzantineServersPorts.add(ServerPortMap.get(server));
        }
        for (String peer : servers) { 
            try {
                URL url = new URL("http://" + peer + "/api/servers/byzantine");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = byzantineServersPorts.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                } else {
                    System.out.println("Error response from server: " + conn.getResponseMessage());
                }

            } catch (Exception e) {
                System.out.println("Error while sending primary servers to peer: " + peer);
            }
        }
    }

    private static void LaunchServers(int noOfClusters, int clusterSize) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Do you want to modify configuration? (y/n)");
        String choice = sc.next();
        if (choice.equals("y")) {
            System.out.println("Enter the number of clusters : ");
            noOfClusters = sc.nextInt();
            System.out.println("Enter the number of servers in each cluster : ");
            clusterSize = sc.nextInt();
            shardMap = createShardMappings(noOfClusters, shardSize);
        }
        String jarPath = "G:/WorkSpace/CSE-535 Distributed Systems/2pcbyz-kallagoutham/two-phase-pbft/target/two-phase-pbft-0.0.1-SNAPSHOT.jar";
        int counter = 0;
        long cluster = 1;
        for (int i = 0; i < noOfClusters; i++) {
            for (int j = 0; j < clusterSize; j++) {
                try {
                    String[] command = {
                        "cmd", "/c", "start",
                        "java", "-Xms64m", "-Xmx128m", String.format("-DCLUSTER=%d", cluster),
                        String.format("-DCLUSTER_SIZE=%d", clusterSize),
                        String.format("-DSHARD_SIZE=%d", shardSize),String.format("-DNO_OF_CLUSTERS=%d", noOfClusters), "-jar", jarPath, String.format("--server.port=%d", 8080 + counter)
                    };
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.start();
                    System.out.println("Started server on port: " + (8080 + counter));
                } catch (IOException e) {
                    System.err.println("Failed to start server on port: " + (8080 + counter));
                } finally {
                    counter++;
                }
            }
            cluster++;
        }
    }

    private static String getDisconnectedServers(String liveServers, int noOfClusters, int clusterSize) {
        String allServers = generateAllServers(noOfClusters, clusterSize);
        List<String> allServersList = Arrays.asList(allServers.split(","));
        List<String> liveServersList = Arrays.asList(liveServers.replaceAll("\\s+", "").split(","));
        List<String> disconnectedServers = allServersList.stream()
                .filter(element -> !liveServersList.contains(element))
                .collect(Collectors.toList());
        return String.join(",", disconnectedServers);

    }

    public static String generateAllServers(int noOfClusters, int clusterSize) {
        int totalServers = noOfClusters * clusterSize;
        StringBuilder allServersBuilder = new StringBuilder();
        for (int i = 1; i <= totalServers; i++) {
            allServersBuilder.append("S").append(i);
            ServerPortMap.put("S" + i, 8080 + i - 1);
            if (i < totalServers) {
                allServersBuilder.append(",");
            }
        }
        return allServersBuilder.toString();
    }

    public static NavigableMap<Integer, Integer> createShardMappings(int noOfClusters, int shardSize) {
        NavigableMap<Integer, Integer> shardMap = new TreeMap<>();
        int start = 1;
        for (int cluster = 1; cluster <= noOfClusters; cluster++) {
            int end = start + shardSize - 1;
            shardMap.put(end, cluster);
            start = end + 1;
        }
        return shardMap;
    }

    public static List<String> createAllServers(int noOfClusters, int clusterSize) {
        List<String> servers = new ArrayList<>();
        int counter = 0;
        for (int i = 0; i < noOfClusters; i++) {
            for (int j = 0; j < clusterSize; j++) {
                servers.add("localhost:"+(8080+counter));
                counter++;
            }
        }
        return servers;
    }

    public static boolean isIntraShardTransaction(String transaction) {
        String[] transactionParts = transaction.split(",");
        int from = Integer.parseInt(transactionParts[0].trim());
        int to = Integer.parseInt(transactionParts[1].trim());
        int fromCluster = shardMap.ceilingEntry(from).getValue();
        int toCluster = shardMap.ceilingEntry(to).getValue();
        return fromCluster == toCluster;
    }

    public static int getCluster(int clientId) {
        Map.Entry<Integer, Integer> entry = shardMap.ceilingEntry(clientId);
        if (entry != null) {
            return entry.getValue();
        }
        throw new IllegalArgumentException("Transaction ID is out of range!");
    }

    public static void printShardMap(NavigableMap<Integer, Integer> shardMap) {
        int previousEnd = 0;
        for (Map.Entry<Integer, Integer> entry : shardMap.entrySet()) {
            int upperBound = entry.getKey();
            int cluster = entry.getValue();
            int lowerBound = previousEnd + 1;
            System.out.println("Cluster " + cluster + ": " + lowerBound + "-" + upperBound);
            previousEnd = upperBound;
        }
    }

    public static void printDataStore(int totalServers) {
        System.out.println("Server : DataStore");
        for(int i=1;i<=totalServers;i++){
                try {
            String urlString = "http://localhost:" + ServerPortMap.get("S"+i) + "/api/datastore";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    System.out.println("S"+i+" : " + response.toString());
                }
            }else{
                System.out.println("S"+i+" : "+"Failed to get data store");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        }
    }

    public static void printPerformanceMetrics(String server) {
        try {
            String urlString = "http://localhost:" + ServerPortMap.get(server) + "/api/performance";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    System.out.println("Server Performance Metrics : " + response.toString());
                }
            } else {
                System.out.println("Failed to get performance metrics");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void printBalance(String client,int clusterSize) {
        int cluster = getCluster(Integer.parseInt(client));
        System.out.println("Server\tclient");
        System.out.println("------------");
        for(int i=8080+(cluster-1)*clusterSize;i<8080+cluster*clusterSize;i++){
        try {
            String urlString = "http://localhost:" + i + "/api/bank/balance?accountId=" + client;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    System.out.println("S"+(i-8079)+"\t"+ response.toString());
                }
            } else {
                System.out.println("Failed: " + conn.getResponseMessage());
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        }
    }

    private static void exchangeKeys() {
        System.out.println("Exchanging keys with peers...");
        for (int i=8080;i<8092;i++) {
            try {
                URL url = new URL("http://localhost:" + i + "/api/servers/publickeys");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.getResponseCode();
            } catch (Exception e) {
                System.out.println("Error while exchanging keys on peer: " + i);
            }
        }
        for (int i=8080;i<8092;i++) {
            try {
                URL url = new URL("http://localhost:" + i + "/api/receive/key");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                String jsonInputString = String.format(
                        "{\"server\":\"%d\",\"publicKey\":\"%s\"}",
                        0,Base64.getEncoder().encodeToString(getPublicKey().getEncoded())
                );
                try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
                conn.getResponseCode();

            } catch (Exception e) {
                System.out.println("Error while exchanging keys on peer: " + i);
            }
        }
        System.out.println("Key exchange complete.");
    }

    public static void generateWorkload(int operations, int maxAccounts, int maxClusters, int clusterSize) { 
        for (int i = 0; i < operations; i++) {
            int operationType = random.nextInt(2); 
            try {
                switch (operationType) {
                    case 0 -> { 
                        int set = random.nextInt(10) + 1; 
                        TransactionData td = createRandomTransactionData(set, maxAccounts, maxClusters, clusterSize);
                        processTransactions(td,clusterSize);
                    }
                    case 1 -> { 
                        int accountId = random.nextInt(maxAccounts) + 1; 
                        printBalance(String.valueOf(accountId),clusterSize);
                    }
                    default -> throw new IllegalStateException("Unexpected operation type: " + operationType);
                }
            } catch (Exception e) {
                System.err.println("Error during workload generation: " + e.getMessage());
            }
        }
    }

    private static TransactionData createRandomTransactionData(int set, int maxAccounts, int maxClusters, int clusterSize) {
        TransactionData transactionData = new TransactionData();
        for (int i = 0; i < 500; i++) { 
            int sender = random.nextInt(maxAccounts) + 1;
            int receiver = random.nextInt(maxAccounts) + 1;
            int amount = random.nextInt(1000) + 1;
            transactionData.transactions.add(sender + "," + receiver + "," + amount);
        }
        for (int cluster = 1; cluster <= maxClusters; cluster++) {
            int clusterStart = (cluster - 1) * clusterSize + 1; 
            int clusterEnd = cluster * clusterSize;      
            int primaryServer = random.nextInt(clusterEnd - clusterStart + 1) + clusterStart;
            transactionData.primaryServers.add("S" + primaryServer);
        }

        return transactionData;
    }

}
