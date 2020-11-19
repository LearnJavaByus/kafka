/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.clients.producer;

import org.apache.kafka.common.Configurable;

/**
 * A plugin interface that allows you to intercept (and possibly mutate) the records received by the producer before
 * they are published to the Kafka cluster.
 * <p>
 * This class will get producer config properties via <code>configure()</code> method, including clientId assigned
 * by KafkaProducer if not specified in the producer config. The interceptor implementation needs to be aware that it will be
 * sharing producer config namespace with other interceptors and serializers, and ensure that there are no conflicts.
 * <p>
 * Exceptions thrown by ProducerInterceptor methods will be caught, logged, but not propagated further. As a result, if
 * the user configures the interceptor with the wrong key and value type parameters, the producer will not throw an exception,
 * just log the errors.
 * <p>
 * ProducerInterceptor callbacks may be called from multiple threads. Interceptor implementation must ensure thread-safety, if needed.
 * <p>
 * Implement {@link org.apache.kafka.common.ClusterResourceListener} to receive cluster metadata once it's available. Please see the class documentation for ClusterResourceListener for more information.
 *
 * 主要用来对消息进行拦截或者修改，也可以用于Producer的Callback回调之前进行相应的预处理。
 */
public interface ProducerInterceptor<K, V> extends Configurable {
    /**
     * This is called from {@link org.apache.kafka.clients.producer.KafkaProducer#send(ProducerRecord)} and
     * {@link org.apache.kafka.clients.producer.KafkaProducer#send(ProducerRecord, Callback)} methods, before key and value
     * get serialized and partition is assigned (if partition is not specified in ProducerRecord).
     * <p>
     * This method is allowed to modify the record, in which case, the new record will be returned. The implication of modifying
     * key/value is that partition assignment (if not specified in ProducerRecord) will be done based on modified key/value,
     * not key/value from the client. Consequently, key and value transformation done in onSend() needs to be consistent:
     * same key and value should mutate to the same (modified) key and value. Otherwise, log compaction would not work
     * as expected.
     * <p>
     * Similarly, it is up to interceptor implementation to ensure that correct topic/partition is returned in ProducerRecord.
     * Most often, it should be the same topic/partition from 'record'.
     * <p>
     * Any exception thrown by this method will be caught by the caller and logged, but not propagated further.
     * <p>
     * Since the producer may run multiple interceptors, a particular interceptor's onSend() callback will be called in the order
     * specified by {@link org.apache.kafka.clients.producer.ProducerConfig#INTERCEPTOR_CLASSES_CONFIG}. The first interceptor
     * in the list gets the record passed from the client, the following interceptor will be passed the record returned by the
     * previous interceptor, and so on. Since interceptors are allowed to modify records, interceptors may potentially get
     * the record already modified by other interceptors. However, building a pipeline of mutable interceptors that depend on the output
     * of the previous interceptor is discouraged, because of potential side-effects caused by interceptors potentially failing to
     * modify the record and throwing an exception. If one of the interceptors in the list throws an exception from onSend(), the exception
     * is caught, logged, and the next interceptor is called with the record returned by the last successful interceptor in the list,
     * or otherwise the client.
     *
     * @param record the record from client or the record returned by the previous interceptor in the chain of interceptors.
     * @return producer record to send to topic/partition
     *
     * Producer在将消息序列化和分配分区之前会调用拦截器的这个方法来对消息进行相应的操作。一般来说最好不要修改消息ProducerRecord的topic、key以及partition等信息，
     * 如果要修改，也需确保对其有准确的判断，否则会与预想的效果出现偏差。比如修改key不仅会影响分区的计算，同样也会影响Broker端日志压缩（Log Compaction）的功能。
     */
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record);

    /**
     * This method is called when the record sent to the server has been acknowledged, or when sending the record fails before
     * it gets sent to the server.
     * <p>
     * This method is generally called just before the user callback is called, and in additional cases when <code>KafkaProducer.send()</code>
     * throws an exception.
     * <p>
     * Any exception thrown by this method will be ignored by the caller.
     * <p>
     * This method will generally execute in the background I/O thread, so the implementation should be reasonably fast.
     * Otherwise, sending of messages from other threads could be delayed.
     *
     * @param metadata The metadata for the record that was sent (i.e. the partition and offset).
     *                 If an error occurred, metadata will contain only valid topic and maybe
     *                 partition. If partition is not given in ProducerRecord and an error occurs
     *                 before partition gets assigned, then partition will be set to RecordMetadata.NO_PARTITION.
     *                 The metadata may be null if the client passed null record to
     *                 {@link org.apache.kafka.clients.producer.KafkaProducer#send(ProducerRecord)}.
     * @param exception The exception thrown during processing of this record. Null if no error occurred.
     *
     *  在消息被应答（Acknowledgement）之前或者消息发送失败时调用，优先于用户设定的Callback之前执行。这个方法运行在Producer的IO线程中，
     *                  所以这个方法里实现的代码逻辑越简单越好，否则会影响消息的发送速率。
     */
    public void onAcknowledgement(RecordMetadata metadata, Exception exception);

    /**
     * This is called when interceptor is closed  关闭当前的拦截器，此方法主要用于执行一些资源的清理工作
     */
    public void close();
}
