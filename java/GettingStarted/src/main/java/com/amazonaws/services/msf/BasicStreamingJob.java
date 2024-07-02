package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A basic Flink Java application to run on Amazon Managed Service for Apache Flink,
 * with Kinesis Data Streams as source and sink.
 */
public class BasicStreamingJob {

    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";
    private static final String DEFAULT_REGION = "ap-southeast-1";
    private static final String DEFAULT_INPUT_STREAM = "ExampleInputStream";
    private static final String DEFAULT_OUTPUT_STREAM = "ExampleOutputStream";

    /**
     * Get configuration properties from Amazon Managed Service for Apache Flink runtime properties
     * in the GroupID "FlinkApplicationProperties", or from command line parameters when running locally
     */
    private static ParameterTool loadApplicationParameters(String[] args, StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            return ParameterTool.fromArgs(args); // If running locally in IntelliJ then get arguments from the run configuration
        } else {
            Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
            Properties flinkProperties = applicationProperties.get(APPLICATION_CONFIG_GROUP);
            if (flinkProperties == null) {
                throw new RuntimeException("Unable to load FlinkApplicationProperties from runtime properties");
            }
            Map<String, String> map = new HashMap<>(flinkProperties.size());
            flinkProperties.forEach((k, v) -> map.put((String) k, (String) v));
            return ParameterTool.fromMap(map);
        }
    }

    private static FlinkKinesisConsumer<String> createSource(ParameterTool applicationProperties) {
        Properties inputProperties = new Properties();
        inputProperties.setProperty(AWSConfigConstants.AWS_REGION,
                                    applicationProperties.get("InputStreamRegion", DEFAULT_REGION));
        inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST"); // Use TRIM_HORIZON to start reading from the earliest record possible

        return new FlinkKinesisConsumer<>(
                applicationProperties.get("InputStreamName", DEFAULT_INPUT_STREAM),
                new SimpleStringSchema(),
                inputProperties);
    }

    private static KinesisStreamsSink<String> createSink(ParameterTool applicationProperties) {
        Properties outputProperties = new Properties();
        outputProperties.setProperty(AWSConfigConstants.AWS_REGION,
                                     applicationProperties.get("OutputStreamRegion", DEFAULT_REGION));

        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setStreamName(applicationProperties.get("OutputStreamName", DEFAULT_OUTPUT_STREAM))
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final ParameterTool applicationParameters = loadApplicationParameters(args, env);

        SourceFunction<String> source = createSource(applicationParameters);
        DataStream<String> input = env.addSource(source, "Kinesis source");

        Sink<String> sink = createSink(applicationParameters);
        input.sinkTo(sink);

        env.execute("Flink Java skeleton with Kinesis Data Streams");
    }

}
