# GridGain 9 Examples

## Overview

This project contains code examples for GridGain 9.

Examples are shipped as a separate Gradle module, so to start running you simply need
to import the provided `build.gradle` file into your favourite IDE.

The following examples are included:
* `RecordViewExample` - demonstrates the usage of the `org.apache.ignite.table.RecordView` API
* `KeyValueViewExample` - demonstrates the usage of the `org.apache.ignite.table.KeyValueView` API
* `SqlJdbcExample` - demonstrates the usage of the Apache Ignite JDBC driver.
* `SqlApiExample` - demonstrates the usage of the Java API for SQL.
* `VolatilePageMemoryStorageExample` - demonstrates the usage of the PageMemory storage engine configured with an in-memory data region.
* `PersistentPageMemoryStorageExample` - demonstrates the usage of the PageMemory storage engine configured with a persistent data region.
* `RocksDbStorageExample` - demonstrates the usage of the RocksDB storage engine.
* `KeyValueViewDataStreamerExample` - demonstrates the usage of the `DataStreamerTarget#streamData(Publisher, DataStreamerOptions)` API 
with the `KeyValueView`. 
* `KeyValueViewPojoDataStreamerExample` - demonstrates the usage of the `DataStreamerTarget#streamData(Publisher, DataStreamerOptions)` API 
with the `KeyValueView` and user-defined POJOs.
* `RecordViewDataStreamerExample` - demonstrates the usage of the `DataStreamerTarget#streamData(Publisher, DataStreamerOptions)` API 
with the `RecordView`.
* `RecordViewPojoDataStreamerExample` - demonstrates the usage of the `DataStreamerTarget#streamData(Publisher, DataStreamerOptions)` API 
with the `RecordView` and user-defined POJOs.
* `ReceiverStreamProcessingExample` - demonstrates the usage of 
the `DataStreamerTarget#streamData(Publisher, Function, Function, ReceiverDescriptor, Subscriber, DataStreamerOptions, Object)` API 
for stream processing of the trades data read from the file.
* `ReceiverStreamProcessingWithResultSubscriberExample` - demonstrates the usage of 
the `DataStreamerTarget#streamData(Publisher, Function, Function, ReceiverDescriptor, Subscriber, DataStreamerOptions, Object)` API 
for stream processing of the trade data and receiving processing results.
* `ReceiverStreamProcessingWithTableUpdateExample` - demonstrates the usage of 
the `DataStreamerTarget#streamData(Publisher, Function, Function, ReceiverDescriptor, Subscriber, DataStreamerOptions, Object)` API 
for stream processing of the trade data and updating account data in the table.
* `ContinuousQueryExample` - demonstrates the usage of the `ContinuousQuerySource` API.
* `ContinuousQueryTransactionsExample` - demonstrates the usage of the `ContinuousQuerySource` API with transactions.
* `ContinuousQueryResumeFromWatermarkExample` - demonstrates the usage of the `ContinuousQuerySource` API resuming from a specific
  watermark.
* `ComputeAsyncExample` - demonstrates the usage of the `IgniteCompute#executeAsync(JobTarget, JobDescriptor, Object)` API.
* `ComputeBroadcastExample` - demonstrates the usage of the `IgniteCompute#execute(BroadcastJobTarget, JobDescriptor, Object)` API.
* `ComputeCancellationExample` - demonstrates the usage of 
the `IgniteCompute#executeAsync(JobTarget, JobDescriptor, Object, CancellationToken)` API.
* `ComputeColocatedExample` - demonstrates the usage of 
the `IgniteCompute#execute(JobTarget, JobDescriptor, Object)` API with colocated JobTarget.
* `ComputeExample` - demonstrates the usage of the `IgniteCompute#execute(JobTarget, JobDescriptor, Object)` API.
* `ComputeJobPriorityExample` - demonstrates the usage of 
the `IgniteCompute#execute(JobTarget, JobDescriptor, Object)` API with different job priorities.
* `ComputeMapReduceExample` - demonstrates the usage of the `IgniteCompute#executeMapReduce(TaskDescriptor, Object)` API.
* `ComputeWithCustomResultMarshallerExample` - demonstrates the usage of the `IgniteCompute#execute(JobTarget, JobDescriptor, Object)` API 
with a custom result marshaller.
* `ComputeWithResultExample` - demonstrates the usage of the `IgniteCompute#execute(JobTarget, JobDescriptor, Object)`}` API 
with a result return.

## Running examples with an GridGain node within a Docker container

1. Pull the docker image
```shell
docker pull gridgain/gridgain9:latest
```

2. Prepare an environment variable:
```shell
GRIDGAIN_SOURCES=/path/to/gridgain9-sources-dir
```

3. Start an GridGain node:
```shell
docker run --name gridgain9-node -d --rm -p 10300:10300 -p 10800:10800 \
  -v $GRIDGAIN_SOURCES/examples/config/ignite-config.conf:/opt/gridgain/etc/gridgain-config.conf gridgain/gridgain9
```

4. Get the IP address of the node:
```shell
NODE_IP_ADDRESS=$(docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' gridgain9-node)
```

5. Initialize the node:
```shell
docker run --rm -it gridgain/gridgain9 cli cluster init --url http://$NODE_IP_ADDRESS:10300 --name myCluster1
```

6. Run the example via IDE.

7. Stop the GridGain node:
```shell
docker stop gridgain9-node
```

## Running examples with an GridGain node started natively

1. Open the Ignite project in your IDE of choice.

2. Download the GridGain ZIP package including the database and CLI parts. Alternatively, build these parts from the GridGain sources 
(see [DEVNOTES.md](../DEVNOTES.md)). Unpack.

3. Prepare the environment variables. `GRIDGAIN_HOME` is used in the GridGain startup. Therefore, you need to export it:
```shell
export GRIDGAIN_HOME=/path/to/gridgain9-db-dir
GRIDGAIN_CLI_HOME=/path/to/gridgain9-cli-dir
GRIDGAIN_SOURCES=/path/to/gridgain9-sources-dir
```

4. Override the default configuration file:
```shell
echo "CONFIG_FILE=$GRIDGAIN_SOURCES/examples/config/ignite-config.conf" >> $GRIDGAIN_HOME/etc/vars.env
```

5. Start an GridGain node using the startup script from the database part:
```shell
$GRIDGAIN_HOME/bin/gridgain9db
```

6. Initialize the cluster using GridGain CLI from the CLI part:
```shell
$GRIDGAIN_CLI_HOME/bin/gridgain9 cluster init --name myCluster1 --config-files=license.conf
```

7. Run the example from the IDE.

8. Stop the GridGain node by stopping the gridgain9db process. See additional details here: https://www.gridgain.com/docs/gridgain9/latest/quick-start/getting-started-guide#stop-the-node
