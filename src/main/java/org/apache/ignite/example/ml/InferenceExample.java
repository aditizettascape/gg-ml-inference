/*
 *  Copyright (C) GridGain Systems. All Rights Reserved.
 *  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.apache.ignite.example.ml;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteServer;
import org.apache.ignite.InitParameters;
import org.apache.ignite.sql.IgniteSql;
import org.gridgain.ml.IgniteMl;
import org.gridgain.ml.model.MlBatchJobParameters;
import org.gridgain.ml.model.MlSimpleJobParameters;
import org.gridgain.ml.model.MlSqlJobParameters;
import org.gridgain.ml.model.ModelConfig;
import org.gridgain.ml.model.ModelType;

/**
 * Example: API-Based Execution
 * This example demonstrates:
 * 1. Registering model metadata only (no cluster deployment)
 * 2. Using API-based (direct) execution for inference:
 *    - Simple prediction
 *    - SQL-based prediction
 *    - Batch prediction
 */
public class InferenceExample {

    private static final String MODEL_ID = "sentiment-model";
    private static final String MODEL_VERSION = "1.0.0";
    //Path to your model files within deployment unit
    private static final String LOCAL_MODEL_PATH = System.getenv("IGNITE_HOME") + "/work/deployment/sentiment-model/1.0.0";
    //Please add your conf path here
    private static final String CONFIG_FILE_PATH = System.getenv("IGNITE_HOME") + "/etc/gridgain-config.conf";
    //Please add your license path here
    private static final String LICENSE_FILE_PATH = System.getenv("IGNITE_HOME") + "/license/license.conf";
    private static final String WORK_FOLDER_PATH = "work";
    private IgniteServer server;
    private IgniteMl mlApi;
    private IgniteSql sql;

    public static void main(String[] args) {
        InferenceExample example = new InferenceExample();

        try {
            example.setup();
            example.setupSampleData();
            example.simpleApiPrediction();
            example.multiplePredictions();
            example.sqlApiPrediction();
            example.parameterizedSqlApiPrediction();
            example.batchApiPrediction();
            example.close();

            System.out.println("All API-based execution examples completed successfully!");

        } catch (Throwable e) {
            System.err.println("Example failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void setup() throws IOException {

        Path myConfig = Path.of(CONFIG_FILE_PATH);
        Path myLicense = Path.of(LICENSE_FILE_PATH);
        Path myWorkDir = Path.of(WORK_FOLDER_PATH);

        if (!Files.exists(myConfig)) {
            throw new RuntimeException("Config file not found at " + myConfig);
        }
        if (!Files.exists(myLicense)) {
            throw new RuntimeException("License file not found at " + myLicense);
        }

        server = IgniteServer.start("defaultNode", myConfig, myWorkDir);

        String myLicenseStr = Files.readString(myLicense);

        InitParameters initParameters = InitParameters.builder()
                .metaStorageNodeNames("defaultNode")
                .clusterName("cluster")
                .clusterConfiguration(myLicenseStr)
                .build();

        server.initCluster(initParameters);

        Ignite ignite = server.api();

        // Get ML API from the embedded Ignite instance
        mlApi = ignite.ml();

        // Get SQL API from the embedded Ignite instance
        sql = ignite.sql();

        System.out.println("GridGain 9 embedded mode initialized successfully");
        System.out.println("Node name: " + ignite.name());
        System.out.println("ML API available through ignite.ml()");
    }

    /**
     * Step 1: Simple API-Based Prediction (Direct Execution)
     */
    private void simpleApiPrediction() {
        System.out.println("\n=== Step 1: Simple API-Based Prediction ===");

        String input = "This movie is absolutely fantastic! I loved every minute of it.";
        System.out.println("Input: " + input);

        try {
            // Create job parameters for prediction
            MlSimpleJobParameters jobParams = MlSimpleJobParameters.builder()
                    .id(MODEL_ID)
                    .version(MODEL_VERSION)
                    .type(ModelType.PYTORCH)
                    .url(LOCAL_MODEL_PATH)
                    .config(ModelConfig.builder().build())
                    .property("input_class", "java.lang.String")
                    .property("output_class", "ai.djl.modality.Classifications")
                    .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                    .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                    .input(input)
                    .build();

            // Using direct execution (uses local MLInferenceService)
            Object result = mlApi.predict(jobParams);

            System.out.println("Direct API prediction result: " + result);

        } catch (Throwable e) {
            System.err.println("Error in simple API prediction: ");
            throw e;
        }
    }

    /**
     * Step 2: Multiple Predictions
     */
    private void multiplePredictions() {
        System.out.println("\n=== Step 2: Multiple Predictions (Caching Demo) ===");

        String[] inputs = {
                "This is a great product!",
                "I hate this service.",
                "This is a great product!", // Duplicate to test caching
                "Average quality, nothing special.",
                "This is a great product!"  // Another duplicate
        };

        System.out.println("Testing caching with duplicate inputs:");

        for (int i = 0; i < inputs.length; i++) {
            String input = inputs[i];
            System.out.println("\nPrediction " + (i + 1) + ":");
            System.out.println("Input: " + input);

            try {
                // Create job parameters for each prediction
                MlSimpleJobParameters jobParams = MlSimpleJobParameters.builder()
                        .id(MODEL_ID)
                        .version(MODEL_VERSION)
                        .type(ModelType.PYTORCH)
                        .url(LOCAL_MODEL_PATH)
                        .config(ModelConfig.builder().build())
                        .property("input_class", "java.lang.String")
                        .property("output_class", "ai.djl.modality.Classifications")
                        .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                        .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                        .input(input)
                        .build();

                long startTime = System.currentTimeMillis();
                Object result = mlApi.predict(jobParams);
                long duration = System.currentTimeMillis() - startTime;

                System.out.println("Result: " + result);
                System.out.println("Duration: " + duration + "ms");

                if (duration < 10) {
                    System.out.println("Fast response - likely cache hit!");
                }

            } catch (Throwable e) {
                System.err.println("Error in multiple predictions");
                throw e;
            }
        }
    }

    /**
     * Step 3: SQL API-Based Prediction
     */
    private void sqlApiPrediction() {
        System.out.println("\n=== Step 3: SQL API-Based Prediction ===");

        // SQL query that returns review text in the first column
        String sqlQuery = "SELECT review_text FROM product_reviews WHERE sentiment IS NULL LIMIT 5";
        System.out.println("SQL Query: " + sqlQuery);

        try {
            // Create job parameters for SQL prediction
            MlSqlJobParameters jobParams = MlSqlJobParameters.builder()
                    .id(MODEL_ID)
                    .version(MODEL_VERSION)
                    .type(ModelType.PYTORCH)
                    .url(LOCAL_MODEL_PATH)
                    .config(ModelConfig.builder().build())
                    .property("input_class", "java.lang.String")
                    .property("output_class", "ai.djl.modality.Classifications")
                    .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                    .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                    .sqlQuery(sqlQuery)
                    .build();

            // Direct API SQL prediction
            List<Object> results = mlApi.predictFromSql(jobParams);

            System.out.println("SQL API-based prediction results:");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("   Result " + (i + 1) + ": " + results.get(i));
            }
            System.out.println("   Executed locally via MLInferenceService");
            System.out.println("   SQL query executed, results processed directly");

        } catch (Throwable e) {
            System.err.println("Error in SQL API prediction");
            throw e;
        }
    }

    /**
     * Step 4: Parameterized SQL API-Based Prediction
     */
    private void parameterizedSqlApiPrediction() {
        System.out.println("\n=== Step 4: Parameterized SQL API-Based Prediction ===");

        // Parameterized SQL query
        String sqlQuery = "SELECT review_text FROM product_reviews WHERE product_category = ? AND sentiment IS NULL LIMIT 3";
        Serializable[] params = {"Electronics"};

        System.out.println("SQL Query: " + sqlQuery);
        System.out.println("Parameters: " + Arrays.toString(params));

        try {
            // Create job parameters for parameterized SQL prediction
            MlSqlJobParameters jobParams = MlSqlJobParameters.builder()
                    .id(MODEL_ID)
                    .version(MODEL_VERSION)
                    .type(ModelType.PYTORCH)
                    .url(LOCAL_MODEL_PATH)
                    .config(ModelConfig.builder().build())
                    .property("input_class", "java.lang.String")
                    .property("output_class", "ai.djl.modality.Classifications")
                    .property("application", "ai.djl.Application$NLP$SENTIMENT_ANALYSIS")
                    .property("translatorFactory", "ai.djl.pytorch.zoo.nlp.sentimentanalysis.PtDistilBertTranslatorFactory")
                    .sqlQuery(sqlQuery)
                    .sqlParams(params)
                    .build();

            // Direct API parameterized SQL prediction
            List<Object> results = mlApi.predictFromSql(jobParams);

            System.out.println("Parameterized SQL API-based prediction results:");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("   Result " + (i + 1) + ": " + results.get(i));
            }

        } catch (Throwable e) {
            System.err.println("Error in parameterized SQL API prediction");
            throw e;
        }
    }

    /**
     * Helper method to set up sample data for SQL examples
     */
    private void setupSampleData() {
        try {
            // Create table (if not exists)
            sql.execute(null,
                    "CREATE TABLE IF NOT EXISTS product_reviews (" +
                            "review_id INT PRIMARY KEY, " +
                            "product_category VARCHAR(100), " +
                            "review_text VARCHAR(1000), " +
                            "sentiment VARCHAR(20)" +
                            ")");

            // Insert sample data
            sql.execute(null,
                    "INSERT INTO product_reviews (review_id, product_category, review_text, sentiment) VALUES " +
                            "(1, 'Electronics', 'This smartphone is amazing! Great battery life.', null), " +
                            "(2, 'Electronics', 'Poor quality headphones, broke after one week.', null), " +
                            "(3, 'Books', 'Excellent book, very informative and well written.', null), " +
                            "(4, 'Electronics', 'Fast laptop, perfect for work and gaming.', null), " +
                            "(5, 'Books', 'Boring story, could not finish reading it.', null), " +
                            "(6, 'Electronics', 'Great value tablet, recommended for students.', null), " +
                            "(7, 'Books', 'Life-changing book, everyone should read this!', null)"
            );

            System.out.println("Sample table 'product_reviews' created with test data");

        } catch (Throwable e) {
            System.out.println("Error in sample data setup");
            throw e;
        }
    }

    private void close() {
        System.out.println("\n=== Cleaning up and shutting down embedded GridGain server ===");
        try {
            // Drop sample table
            sql.execute(null, "DROP TABLE IF EXISTS product_reviews");

            // Stop the embedded server
            if (server != null) {
                server.shutdown();
                System.out.println("Embedded GridGain server shut down successfully");
            }
            System.out.println("=== Disconnected from GG9 ===");
        } catch (Throwable e) {
            System.err.println("Error during cleanup");
            throw e;
        }
    }
}
