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
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteServer;
import org.apache.ignite.InitParameters;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.compute.JobDescriptor;
import org.apache.ignite.compute.JobExecution;
import org.apache.ignite.compute.JobExecutionOptions;
import org.apache.ignite.compute.JobExecutorType;
import org.apache.ignite.compute.JobTarget;
import org.apache.ignite.deployment.DeploymentUnit;
import org.apache.ignite.sql.IgniteSql;
import org.gridgain.ml.compute.MlBatchPredictionJob;
import org.gridgain.ml.compute.MlSimplePredictionJob;
import org.gridgain.ml.compute.MlSqlPredictionJob;
import org.gridgain.ml.model.MlBatchJobParameters;
import org.gridgain.ml.model.MlSimpleJobParameters;
import org.gridgain.ml.model.MlSqlJobParameters;
import org.gridgain.ml.model.ModelConfig;
import org.gridgain.ml.model.ModelType;
import org.gridgain.ml.model.marshalling.MlInputMarshaller;
import org.gridgain.ml.model.marshalling.MlOutputListMarshaller;
import org.gridgain.ml.model.marshalling.MlOutputMarshaller;


/**
 * Complete ML_EMBEDDED compute execution example demonstrating:
 * <p>
 * 1. Simple Prediction using Compute
 * 2. Batch Prediction using Compute
 * 3. SQL Prediction using Compute
 */
public class ComputeExample {

    private static final String MODEL_ID = "sentiment-model";
    private static final String MODEL_VERSION = "1.0.0";
    private static final String CONFIG_FILE_PATH = System.getenv("IGNITE_HOME") + "/etc/gridgain-config.conf";
    private static final String LICENSE_FILE_PATH = System.getenv("IGNITE_HOME") + "/license/license.conf";
    private static final String WORK_FOLDER_PATH = "work";

    private IgniteServer server;
    private Ignite ignite;
    private IgniteClient client;
    private IgniteSql sql;

    public static void main(String[] args) {
        ComputeExample example = new ComputeExample();

        try {
            example.setupEmbeddedServer();
            example.setupClient();

            // Execute all ML_EMBEDDED examples
            example.executeSimpleMLPrediction();
            example.executeBatchMLPrediction();

            example.setupSampleData();
            example.executeSqlMLPrediction();

            System.out.println("All ML_EMBEDDED examples completed successfully!");

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
        ignite = server.api();

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

        sql = ignite.sql();

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
    private void executeSimpleMLPrediction() throws Exception {
        System.out.println("=== Simple ML Prediction ===");

        // Create job parameters
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

        JobDescriptor<MlSimpleJobParameters, Classifications> descriptor = JobDescriptor.builder(
                        MlSimplePredictionJob.<MlSimpleJobParameters, Classifications>jobClass())
                .units(List.of(new DeploymentUnit(MODEL_ID, MODEL_VERSION)))
                .options(JobExecutionOptions.builder()
                        .executorType(JobExecutorType.ML_EMBEDDED)
                        .priority(1)
                        .build())
                .argumentMarshaller(new MlInputMarshaller<>())
                .resultMarshaller(new MlOutputMarshaller<>())
                .build();

        System.out.println("Input: \"" + jobParams.input() + "\"");
        long startTime = System.currentTimeMillis();

        // Execute ML prediction
        JobExecution<Classifications> execution = client.compute().submitAsync(
                JobTarget.anyNode(client.clusterNodes()),
                descriptor,
                jobParams
        ).get();

        Classifications result = execution.resultAsync().get();
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
    private void executeBatchMLPrediction() throws Exception {
        System.out.println("\n=== Batch ML Prediction ===");

        List<String> batchInputs = Arrays.aslist();
//                Arrays.asList(
//                "This movie is very good",
//                "This book is not good",
//                "This food is very good",
//                "This game is not good",
//                "This song is very good",
//                "This show is very good",
//                "This app is very good"
//        );

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

        JobDescriptor<MlBatchJobParameters, List<Classifications>> descriptor = JobDescriptor.builder(
                        MlBatchPredictionJob.<MlBatchJobParameters, Classifications>jobClass())
                .units(List.of(new DeploymentUnit(MODEL_ID, MODEL_VERSION)))
                .options(JobExecutionOptions.builder()
                        .executorType(JobExecutorType.ML_EMBEDDED)
                        .priority(2)
                        .build())
                .argumentMarshaller(new MlInputMarshaller<>())
                .resultMarshaller(new MlOutputListMarshaller<>())
                .build();

        System.out.println("  Batch inputs (" + batchInputs.size() + " items):");
        batchInputs.forEach(input -> System.out.println("   • \"" + input + "\""));
        System.out.println("  Executing batch ML prediction...");

        long startTime = System.currentTimeMillis();

        // Execute batch ML prediction
        JobExecution<List<Classifications>> execution = client.compute().submitAsync(
                JobTarget.anyNode(client.clusterNodes()),
                descriptor,
                jobParams
        ).get();

        List<Classifications> results = execution.resultAsync().get();
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
    private void executeSqlMLPrediction() throws Exception {
        System.out.println("\n=== SQL ML Prediction ===");

        String sqlQuery = "SELECT review_text FROM product_reviews WHERE sentiment IS NULL LIMIT 3";

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
                .sqlParams(null)
                .build();

        JobDescriptor<MlSqlJobParameters, List<Classifications>> descriptor = JobDescriptor.builder(
                        MlSqlPredictionJob.<MlSqlJobParameters, Classifications>jobClass())
                .units(List.of(new DeploymentUnit(MODEL_ID, MODEL_VERSION)))
                .options(JobExecutionOptions.builder()
                        .executorType(JobExecutorType.ML_EMBEDDED)
                        .priority(3)
                        .build())
                .argumentMarshaller(new MlInputMarshaller<>())
                .resultMarshaller(new MlOutputListMarshaller<>())
                .build();

        System.out.println(" SQL Query: " + sqlQuery);
        System.out.println(" Executing SQL-based ML prediction...");

        long startTime = System.currentTimeMillis();

        // Execute SQL ML prediction
        JobExecution<List<Classifications>> execution = client.compute().submitAsync(
                JobTarget.anyNode(client.clusterNodes()),
                descriptor,
                jobParams
        ).get();

        List<Classifications> results = execution.resultAsync().get();
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("  SQL ML Results (" + results.size() + " items):");
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
     * Cleanup resources.
     * This code is unchanged from the original implementation.
     */
    private void cleanup() {
        try {
            System.out.println("Cleanup started");
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
            System.out.println("Cleanup completed!");
        } catch (Exception e) {
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }
}
