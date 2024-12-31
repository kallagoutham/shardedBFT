import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DockerComposeGenerator {

    public static void main(String[] args) {
        int noOfClusters = 3;
        int clusterSize = 4;
        int shardSize = 1000; // Unused but included as an environment variable

        generateDockerCompose(noOfClusters, clusterSize, shardSize);
    }

    public static void generateDockerCompose(int noOfClusters, int clusterSize, int shardSize) {
        StringBuilder dockerCompose = new StringBuilder();
        dockerCompose.append("version: \"3.9\"\n");
        dockerCompose.append("services:\n");

        int counter = 0;

        for (int cluster = 1; cluster <= noOfClusters; cluster++) {
            for (int i = 0; i < clusterSize; i++) {
                dockerCompose.append("  app").append(counter).append(":\n")
                        .append("    build: .\n")
                        .append("    ports:\n")
                        .append("      - \"").append(8080 + counter).append(":8080\"\n")
                        .append("    environment:\n")
                        .append("      - CLUSTER=").append(cluster).append("\n")
                        .append("      - CLUSTER_SIZE=").append(clusterSize).append("\n")
                        .append("      - SHARD_SIZE=").append(shardSize).append("\n")
                        .append("      - NO_OF_CLUSTERS=").append(noOfClusters).append("\n")
                        .append("      - JAVA_OPTS=-Xms64m -Xmx128m\n");
                counter++;
            }
        }

        // Write the content to a docker-compose.yml file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./docker-compose.yml"))) {
            writer.write(dockerCompose.toString());
            System.out.println("docker-compose.yml file generated successfully.");
        } catch (IOException e) {
            System.err.println("Error writing to docker-compose.yml file: " + e.getMessage());
        }
    }
}
