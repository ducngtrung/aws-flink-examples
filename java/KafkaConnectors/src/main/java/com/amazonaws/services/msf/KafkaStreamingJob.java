package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * A basic Flink Java application to run on Amazon Managed Service for Apache Flink,
 * with Kafka as source and sink.
 */
public class KafkaStreamingJob {

    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";
//    private static final String DEFAULT_GROUP_ID = "my-group";
    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_SINK_TOPIC = "destination";
//    private static final String DEFAULT_CLUSTER = "localhost:9092";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamingJob.class);

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

    private static Properties getKafkaProperties(ParameterTool applicationProperties, String prefix){
        Properties properties = new Properties();
        applicationProperties.getProperties().forEach((key, value) -> {
            Optional.ofNullable(key)
                    .map(Object::toString)
                    .filter(k -> k.startsWith(prefix))
                    .ifPresent(k -> {
                        properties.put(k.substring(prefix.length()+1), value);
                    });
        });
        LOG.info(prefix + " Kafka properties: " + properties);
        return properties;
    }

    private static KafkaSource<String> createKafkaSource(ParameterTool applicationProperties) {
        return KafkaSource.<String>builder()
//                .setBootstrapServers(applicationProperties.get("source.bootstrap.servers", DEFAULT_CLUSTER))
                .setTopics(applicationProperties.get("source.topic", DEFAULT_SOURCE_TOPIC))
//                .setGroupId(applicationProperties.get("group.id", DEFAULT_GROUP_ID))
                .setStartingOffsets(OffsetsInitializer.earliest()) // Use earliest() to start reading from the earliest record possible
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperties(getKafkaProperties(applicationProperties,"source"))
                .build();
    }

    private static KafkaSink<String> createKafkaSink(ParameterTool applicationProperties) {
        return KafkaSink.<String>builder()
//                .setBootstrapServers(applicationProperties.get("sink.bootstrap.servers", DEFAULT_CLUSTER))
                .setRecordSerializer(
                    KafkaRecordSerializationSchema.builder()
                        .setTopic(applicationProperties.get("sink.topic", DEFAULT_SINK_TOPIC))
                        .setKeySerializationSchema(new SimpleStringSchema())
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setKafkaProducerConfig(getKafkaProperties(applicationProperties,"sink"))
                .setDeliverGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .build();
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final ParameterTool applicationProperties = loadApplicationParameters(args, env);
        LOG.info("Application properties: {}", applicationProperties.toMap());

        KafkaSource<String> source = createKafkaSource(applicationProperties);
        DataStream<String> inputStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        KafkaSink<String> sink = createKafkaSink(applicationProperties);
        inputStream.sinkTo(sink);

        env.execute("Flink Java skeleton with Kafka Source and Sink");
    }

}
