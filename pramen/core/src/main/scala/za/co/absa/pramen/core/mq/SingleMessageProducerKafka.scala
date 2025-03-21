/*
 * Copyright 2022 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.pramen.core.mq

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import za.co.absa.pramen.core.utils.ConfigUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class SingleMessageProducerKafka(topic: String, kafkaConfig: Config) extends SingleMessageProducer {

  val props: Properties = ConfigUtils.toProperties(kafkaConfig)

  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[org.apache.kafka.common.serialization.StringSerializer])
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[org.apache.kafka.common.serialization.StringSerializer])

  val producer = new KafkaProducer[String, String](props)

  override def send(message: String, numberOrRetries: Int): Unit = {
    val record = new ProducerRecord[String, String](topic, null, message)
    try {
      producer.send(record).get()
    } catch {
      case NonFatal(ex) =>
        if (numberOrRetries <= 0) {
          throw ex
        } else {
          val fut = Future {
            send(message, numberOrRetries - 1)
            true
          }

          // send() can sometime block indefinitely, better to wrap it in a Future and bound the wait.
          Await.result(fut, Duration.create(120, TimeUnit.SECONDS))
        }
    }
  }

}
