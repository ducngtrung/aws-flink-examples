## AVRO serialization in KafkaSource and KafkaSink using AWS Glue Schema Registry

* Flink version: 1.15
* Flink API: DataStream API
* Language: Java (11)

This example demonstrates how to serialize/deserialize AVRO messages in Kafka sources and sinks, using 
[AWS Glue Schema Registry](https://docs.aws.amazon.com/glue/latest/dg/schema-registry.html).

The reader's schema definition for the source and the writer's schema definition for the sink are provided as 
AVRO IDL (`.avdl`) in [./src/main/resources/avro](./src/main/resources/avro).


A KafkaSource produces a stream of AVRO data objects (SpecificRecords), fetching the writer's schema from AWS Glue 
Schema Registry. The AVRO Kafka message value must have been serialized using AWS Glue Schema Registry.

A KafkaSink serializes AVRO data objects as Kafka message value, and a String (converted to bytes as UTF-8) as Kafka 
message key.

## Flink compatibility

**Note:** This project is compatible with Flink 1.15+ and Amazon Managed Service for Apache Flink

### Flink API compatibility

This example shows how to use AWS Glue Schema Registry with the Flink Java DataStream API.

It uses the newer `KafkaSource` and `KafkaSink` (as opposed to `FlinkKafkaConsumer` and `FlinkKafkaProducer`, deprecated 
with Flink 1.15).

At the moment, no format provider is available for the Table API.

## Notes about using AVRO with Apache Flink

### AVRO-generated classes

This project uses classes generated at build time as data objects.

As a best practice, only the AVRO schema definitions (IDL `.avdl` files in this case) are included in the project source 
code. 

AVRO Maven plugin generates the Java classes at build time, during the 
[`generate-source`](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html) phase.

The generated classes are written into `./target/generated-sources/avro` directory and should **not** be committed with 
the project source code.

This way, the only dependency is on the schema definition file(s).
If any change is required, the schema file is modified and the AVRO classes are re-generated automatically in the build.

Code generation is supported by all common IDEs like IntelliJ. 
If your IDE does not see the AVRO classes (`TemperatureSample` and `RoomTemperature`) when you import the project for the 
first time, you may manually run `mvn generate-sources` once to force source code generation from the IDE.

### AVRO-generated classes (SpecificRecord) in Apache Flink

Using AVRO-generated classes (SpecificRecord) within the flow of the Flink application (between operators) or in the 
Flink state has an additional benefit. 
Flink will [natively and efficiently serialize and deserialize](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/serialization/types_serialization/#pojos) 
these objects, without risking of falling back to Kryo.

### AVRO and AWS Glue Schema Registry dependencies

The following dependencies related to AVRO and AWS Glue Schema Registry are included (for Flink 1.15.2):

1. `org.apache.flink:flink-avro-glue-schema-registry_2.12:1.15.2` - Support for AWS Glue Schema Registry SerDe
2. `org.apache.avro:avro:1.10.2` - Overrides AVRO 1.10.0, transitively included.

The project also includes `org.apache.flink:flink-avro:1.15.2`. 
This is already a transitive dependency from the AWS Glue Schema Registry SerDe and is defined explicitly only for clarity.

Note that we are overriding AVRO 1.10.0 with 1.10.2. 
This minor version upgrade does not break the internal API, and includes some bug fixes introduced with 
AVRO [1.10.1](https://github.com/apache/avro/releases/tag/release-1.10.1)
and [1.10.2](https://github.com/apache/avro/releases/tag/release-1.10.2). 

### Running in IntelliJ

To start the Flink job in IntelliJ, edit the Run/Debug configuration to enable 'Add dependencies with "provided" scope to
the classpath'.