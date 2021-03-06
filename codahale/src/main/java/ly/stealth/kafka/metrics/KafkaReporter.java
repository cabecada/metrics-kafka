/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ly.stealth.kafka.metrics;

import com.codahale.metrics.*;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

public class KafkaReporter extends ScheduledReporter {
    private static final Logger log = LoggerFactory.getLogger(KafkaReporter.class);

    private final Producer<String, String> kafkaProducer;
    private final String kafkaTopic;
    private final ObjectMapper mapper;
    private final MetricRegistry registry;

    private KafkaReporter(MetricRegistry registry,
                          String name,
                          MetricFilter filter,
                          TimeUnit rateUnit,
                          TimeUnit durationUnit,
                          String kafkaTopic,
                          Properties kafkaProperties) {
        super(registry, name, filter, rateUnit, durationUnit);
        this.registry = registry;
        mapper = new ObjectMapper().registerModule(new MetricsModule(rateUnit,
                                                                     durationUnit,
                                                                     false));
        this.kafkaTopic = kafkaTopic;
        kafkaProducer = new Producer<String, String>(new ProducerConfig(kafkaProperties));
    }

    @Override
    public synchronized void report(SortedMap<String, Gauge> gauges,
                                    SortedMap<String, Counter> counters,
                                    SortedMap<String, Histogram> histograms,
                                    SortedMap<String, Meter> meters,
                                    SortedMap<String, Timer> timers) {
        try {
            log.info("Trying to report metrics to Kafka kafkaTopic {}", kafkaTopic);
            StringWriter report = new StringWriter();
            mapper.writeValue(report, registry);
            log.debug("Created metrics report: {}", report);
            kafkaProducer.send(new KeyedMessage<String, String>(kafkaTopic, report.toString()));
            log.info("Metrics were successfully reported to Kafka kafkaTopic {}", kafkaTopic);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static Builder builder(MetricRegistry registry, String brokerList, String kafkaTopic) {
        return new Builder(registry, kafkaTopic, brokerList);
    }

    public static class Builder {
        private MetricRegistry registry;
        private String kafkaTopic;
        private String brokerList;

        private boolean synchronously = false;
        private int compressionCodec = 0;
        private int batchSize = 200;
        private int messageSendMaxRetries = 3;

        private String name = "KafkaReporter";
        private MetricFilter filter = MetricFilter.ALL;
        private TimeUnit rateUnit = TimeUnit.SECONDS;
        private TimeUnit durationUnit = TimeUnit.SECONDS;

        public Builder(MetricRegistry registry, String topic, String brokerList) {
            this.registry = registry;
            this.kafkaTopic = topic;
            this.brokerList = brokerList;
        }

        public String getKafkaTopic() {
            return kafkaTopic;
        }

        public Builder setKafkaTopic(String kafkaTopic) {
            this.kafkaTopic = kafkaTopic;
            return this;
        }

        public String getBrokerList() {
            return brokerList;
        }

        public Builder setBrokerList(String brokerList) {
            this.brokerList = brokerList;
            return this;
        }

        public boolean isSynchronously() {
            return synchronously;
        }

        public Builder setSynchronously(boolean synchronously) {
            this.synchronously = synchronously;
            return this;
        }

        public int getCompressionCodec() {
            return compressionCodec;
        }

        public Builder setCompressionCodec(int compressionCodec) {
            this.compressionCodec = compressionCodec;
            return this;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public Builder setBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public int getMessageSendMaxRetries() {
            return messageSendMaxRetries;
        }

        public Builder setMessageSendMaxRetries(int messageSendMaxRetries) {
            this.messageSendMaxRetries = messageSendMaxRetries;
            return this;
        }

        public MetricRegistry getRegistry() {
            return registry;
        }

        public Builder setRegistry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        public String getName() {
            return name;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public MetricFilter getFilter() {
            return filter;
        }

        public Builder setFilter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public TimeUnit getRateUnit() {
            return rateUnit;
        }

        public Builder setRateUnit(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        public TimeUnit getDurationUnit() {
            return durationUnit;
        }

        public Builder setDurationUnit(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        public KafkaReporter build() {
            Properties props = new Properties();
            props.put("metadata.broker.list", brokerList);
            props.put("serializer.class", "kafka.serializer.StringEncoder");
            props.put("producer.type", synchronously ? "sync" : "async");
            props.put("compression.codec", String.valueOf(compressionCodec));
            props.put("batch.num.messages", String.valueOf(batchSize));
            props.put("message.send.max.retries", String.valueOf(messageSendMaxRetries));
            props.put("compression.codec", String.valueOf(compressionCodec));

            return new KafkaReporter(registry, name, filter, rateUnit, durationUnit, kafkaTopic, props);
        }
    }
}
