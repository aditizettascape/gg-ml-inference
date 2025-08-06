/*
 *  Copyright (C) GridGain Systems. All Rights Reserved.
 *  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.apache.ignite.example.ml;

import ai.djl.modality.Classifications;
import ai.djl.modality.Classifications.Classification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.ignite.IgniteServer;
import org.apache.ignite.InitParameters;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.IgniteSql;
import org.apache.ignite.table.Tuple;
import org.gridgain.ml.IgniteMl;
import org.gridgain.ml.model.MlBatchJobParameters;
import org.gridgain.ml.model.MlColocatedJobParameters;
import org.gridgain.ml.model.MlSimpleJobParameters;
import org.gridgain.ml.model.MlSqlJobParameters;
import org.gridgain.ml.model.ModelConfig;
import org.gridgain.ml.model.ModelType;

/**
 * Example demonstrating ClientMl usage.
 *
 * <p>This example shows how ClientMl internally leverages ClientCompute while
 * providing users with a simple, clean ML API.
 *
 */
public class ClientExample extends ComputeExample {

    private static final String MODEL_ID = "sentiment-model";
    private static final String MODEL_VERSION = "1.0.0";
    private static final String CONFIG_FILE_PATH = System.getenv("IGNITE_HOME") + "/etc/gridgain-config.conf";
    private static final String LICENSE_FILE_PATH = System.getenv("IGNITE_HOME") + "/license/license.conf";
    private static final String WORK_FOLDER_PATH = "work";

    private IgniteServer server;
    private IgniteClient client;
    private IgniteMl ml;
    private IgniteSql sql;

    public static void main(String[] args) {
        ClientExample example = new ClientExample();

        try {
            example.setupEmbeddedServer();
            example.setupClient();

            example.executeSimplePrediction();
            example.executeBatchPrediction();
            example.setupSampleData();
            example.executeSqlPrediction();
            example.executeColocatedMLPrediction();

        } catch (Throwable e) {
            System.err.println("Example failed: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            example.cleanup();
        }
    }

    private void setupEmbeddedServer() throws IOException {
        System.out.println("Setting up GridGain embedded server...");

        Path configPath = Paths.get(CONFIG_FILE_PATH);
        Path licensePath = Paths.get(LICENSE_FILE_PATH);
        Path workDir = Paths.get(WORK_FOLDER_PATH);

        validatePaths(configPath, licensePath);

        server = IgniteServer.start("defaultNode", configPath, workDir);

        String licenseStr = Files.readString(licensePath);
        InitParameters initParameters = InitParameters.builder()
                .metaStorageNodeNames("defaultNode")
                .clusterName("cluster")
                .clusterConfiguration(licenseStr)
                .build();

        server.initCluster(initParameters);

        System.out.println("Embedded server initialized");
    }

    private void validatePaths(Path configPath, Path licensePath) {
        if (!Files.exists(configPath)) {
            throw new RuntimeException("Config file not found at " + configPath);
        }
        if (!Files.exists(licensePath)) {
            throw new RuntimeException("License file not found at " + licensePath);
        }
    }

    private void setupClient() {
        System.out.println("Setting up client connection for ML_EMBEDDED compute...");

        client = IgniteClient.builder()
                .addresses("127.0.0.1:10800")
                .build();

        ml = client.ml();
        sql = client.sql();

        System.out.println("Client connection established");
    }

    private void setupSampleData() {
        System.out.println("Setting up sample data for SQL ML prediction...");

        try {
            sql.execute(null,
                    "CREATE TABLE IF NOT EXISTS product_reviews (" +
                            "review_id INT PRIMARY KEY, " +
                            "product_category VARCHAR(100), " +
                            "review_text VARCHAR(1000), " +
                            "sentiment VARCHAR(20)" +
                            ")");

            sql.execute(null,
                    "INSERT INTO product_reviews (review_id, product_category, review_text, sentiment) VALUES " +
                            "(1, 'Electronics', 'This smartphone is amazing! Great battery life.', null), " +
                            "(2, 'Electronics', 'Poor quality headphones, broke after one week.', null), " +
                            "(3, 'Books', 'Excellent book, very informative and well written.', null), " +
                            "(4, 'Electronics', 'Fast laptop, perfect for work and gaming.', null), " +
                            "(5, 'Books', 'Boring story, could not finish reading it.', null)"
            );

            System.out.println("Sample data setup complete");
        } catch (Exception e) {
            System.out.println("Sample data setup failed (may already exist): " + e.getMessage());
        }
    }

    /**
     * Example 1: Simple ML prediction.
     */
    private void executeSimplePrediction() {
        System.out.println("\n=== Simple Prediction - Clean ML API ===");

        // Create job parameters - same as embedded mode!
        MlSimpleJobParameters jobParams = MlSimpleJobParameters.builder()
                .id(MODEL_ID)
                .version(MODEL_VERSION)
                .type(ModelType.PYTORCH)
                .config(ModelConfig.builder().build())
                .property("input_class", String.class.getName())
                .property("output_class", Classifications.class.getName())
                .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                .input("This movie is absolutely fantastic! I loved every minute of it.")
                .build();

        System.out.println(" Input: \"" + jobParams.input() + "\"");
        System.out.println(" Executing via clean ML API...");

        long startTime = System.currentTimeMillis();

        Classifications result = ml.predict(jobParams);
        long duration = System.currentTimeMillis() - startTime;
        Classification best = result.best();
        System.out.printf("   %s (%.2f%%)\n",
                best.getClassName(),
                best.getProbability() * 100);

        System.out.println("Duration: " + duration + "ms");
        System.out.println("Simple ML prediction complete!");
    }

    /**
     * Example 2: Batch ML prediction.
     */
    private void executeBatchPrediction() {
        System.out.println("\n=== Batch ML Prediction ===");

        List<String> batchInputs = Arrays.asList(
                "This movie is very good",
                "This book is not good",
                "This food is very good",
                "This game is not good"
        );

        // Create batch job parameters
        MlBatchJobParameters jobParams = MlBatchJobParameters.builder()
                .id(MODEL_ID)
                .version(MODEL_VERSION)
                .type(ModelType.PYTORCH)
                .config(ModelConfig.builder().batchSize(4).build())
                .property("input_class", String.class.getName())
                .property("output_class", Classifications.class.getName())
                .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                .batchInput(batchInputs)
                .build();

        System.out.println("📝 Batch inputs (" + batchInputs.size() + " items)");
        System.out.println("🔄 Executing batch prediction...");

        long startTime = System.currentTimeMillis();

        List<Classifications> results = ml.batchPredict(jobParams);
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("  Batch Results (" + results.size() + " ): items");
        for (int i = 0; i < results.size(); i++) {
            Classifications classification = results.get(i);
            Classification best = classification.best();
            System.out.printf("   %d. \"%s\" → %s (%.2f%%)\n",
                    i + 1,
                    batchInputs.get(i),
                    best.getClassName(),
                    best.getProbability() * 100);
        }

        System.out.println("   Total processing time: " + duration + "ms");
        System.out.println("   Average per item: " + (duration / batchInputs.size()) + "ms");
        System.out.println("Batch ML prediction complete!");
    }

    /**
     * Example 3: SQL ML prediction.
     */
    private void executeSqlPrediction() {
        System.out.println("\n=== SQL ML Prediction ===");

        String sqlQuery = "SELECT 'This is a great product!' as review_text " +
                "UNION ALL SELECT 'Poor quality, very disappointed' as review_text " +
                "UNION ALL SELECT 'Amazing value for money' as review_text";

        // Create SQL job parameters
        MlSqlJobParameters jobParams = MlSqlJobParameters.builder()
                .id(MODEL_ID)
                .version(MODEL_VERSION)
                .type(ModelType.PYTORCH)
                .config(ModelConfig.builder().build())
                .property("input_class", String.class.getName())
                .property("output_class", Classifications.class.getName())
                .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                .sqlQuery(sqlQuery)
                .build();

        System.out.println(" SQL Query: " + sqlQuery);
        System.out.println(" Executing SQL prediction...");

        long startTime = System.currentTimeMillis();

        List<Classifications> results = ml.predictFromSql(jobParams);

        long duration = System.currentTimeMillis() - startTime;

        System.out.println(" SQL ML Results (" + results.size() + " items):");
        for (int i = 0; i < results.size(); i++) {
            Classifications classification = results.get(i);
            Classification best = classification.best();
            System.out.printf("   %d → %s (%.2f%%)\n",
                    i + 1,
                    best.getClassName(),
                    best.getProbability() * 100);
        }

        System.out.println("   Total processing time: " + duration + "ms");
        System.out.println("   Average per item: " + (duration / results.size()) + "ms");
        System.out.println("SQL ML prediction complete!");
    }

    /**
     * Example 4: Colocated ML prediction.
     */
    private void executeColocatedMLPrediction() throws Exception {
        System.out.println("=== Colocated ML Prediction ===");

        // Create Colocated job parameters
        MlColocatedJobParameters jobParams = MlColocatedJobParameters.builder()
                .id(MODEL_ID)
                .version(MODEL_VERSION)
                .type(ModelType.PYTORCH)
                .url("") // Resolved from deployment unit
                .config(ModelConfig.builder().build())
                .property("input_class", String.class.getName())
                .property("output_class", Classifications.class.getName())
                .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                .tableName("product_reviews")
                .key(Tuple.create().set("review_id", 1))
                .inputColumn("review_text")
                .build();

        long startTime = System.currentTimeMillis();

        Classifications result = ml.predictColocated(jobParams);
        long duration = System.currentTimeMillis() - startTime;
        Classification best = result.best();
        System.out.printf("   %s (%.2f%%)\n",
                best.getClassName(),
                best.getProbability() * 100);

        System.out.println("Duration: " + duration + "ms");
        System.out.println("Simple ML prediction complete!");
    }

    private void cleanup() {
        System.out.println("\nCleaning up...");

        if (sql != null) {
            sql.execute(null, "DROP TABLE IF EXISTS product_reviews");
            System.out.println("  Sample data cleaned up");
        }

        if (client != null) {
            client.close();
            System.out.println("  Client connection closed");
        }

        if (server != null) {
            server.shutdown();
            System.out.println("  Embedded server shutdown complete");
        }

        System.out.println("Cleanup complete");
    }
}
