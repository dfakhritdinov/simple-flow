package com.fakhritdinov.kafka.consumer

import cats.effect.{Blocker, ContextShift, Sync}
import com.fakhritdinov.kafka._
import org.apache.kafka.clients.consumer.{
  Consumer => JavaConsumer,
  ConsumerRebalanceListener => JavaConsumerRebalanceListener
}
import org.apache.kafka.common.{TopicPartition => JavaTopicPartition}

import java.util.{Collection => JavaCollection}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

trait Consumer[F[_], K, V] {

  def subscribe(topics: Set[Topic]): F[Unit]

  def subscribe(topics: Set[Topic], listener: BlockingRebalanceListener[K, V]): F[Unit]

  def unsubscribe(): F[Unit]

  def assign(partitions: Set[TopicPartition]): F[Unit]

  def poll(timeout: FiniteDuration): F[Map[TopicPartition, List[ConsumerRecord[K, V]]]]

  def commit(offsets: Map[TopicPartition, Offset]): F[Unit]

  def endOffsets(partitions: Set[TopicPartition]): F[Map[TopicPartition, Offset]]

  def seekToBeginning(partitions: Set[TopicPartition]): F[Unit]

  def wakeup(): F[Unit]

}

object Consumer {

  def apply[F[_]: Sync: ContextShift, K, V](
    consumer: JavaConsumer[K, V],
    blocker:  Blocker
  ): Consumer[F, K, V] = new ConsumerImpl[F, K, V](consumer, blocker)

}

private final class ConsumerImpl[F[_]: Sync: ContextShift, K, V](
  consumer: JavaConsumer[K, V],
  blocker:  Blocker
) extends Consumer[F, K, V] {

  val blockingConsumer = new BlockingConsumer[K, V](consumer)

  def subscribe(topics: Set[Topic]): F[Unit] =
    Sync[F].delay {
      consumer.subscribe(topics.asJavaCollection)
    }

  def subscribe(topics: Set[Topic], listener: BlockingRebalanceListener[K, V]): F[Unit] =
    Sync[F].delay {
      val javaListener = new ListenerImpl[K, V](blockingConsumer, listener)
      consumer.subscribe(topics.asJavaCollection, javaListener)
    }

  def unsubscribe(): F[Unit] =
    Sync[F].delay {
      consumer.unsubscribe()
    }

  def assign(partitions: Set[TopicPartition]): F[Unit] =
    Sync[F].delay {
      consumer.assign(partitions.toJava)
    }

  def poll(timeout: FiniteDuration): F[Map[TopicPartition, List[ConsumerRecord[K, V]]]] =
    blocker.blockOn {
      Sync[F].delay {
        consumer.poll(timeout.toJava).toScala
      }
    }

  def commit(offsets: Map[TopicPartition, Offset]): F[Unit] =
    blocker.blockOn {
      Sync[F].delay {
        consumer.commitSync()
      }
    }

  def endOffsets(partitions: Set[TopicPartition]): F[Map[TopicPartition, Offset]] =
    blocker.blockOn {
      Sync[F].delay {
        consumer.endOffsets(partitions.toJava).toScala
      }
    }

  def seekToBeginning(partitions: Set[TopicPartition]): F[Unit] =
    blocker.blockOn {
      Sync[F].delay {
        consumer.seekToBeginning(partitions.toJava)
      }
    }

  def wakeup(): F[Unit] =
    Sync[F].delay {
      consumer.wakeup()
    }

}

private class ListenerImpl[K, V](
  consumer: BlockingConsumer[K, V],
  listener: BlockingRebalanceListener[K, V]
) extends JavaConsumerRebalanceListener {

  def onPartitionsRevoked(partitions: JavaCollection[JavaTopicPartition]) =
    listener.onPartitionsRevoked(consumer, partitions.toScala)

  def onPartitionsAssigned(partitions: JavaCollection[JavaTopicPartition]) =
    listener.onPartitionsAssigned(consumer, partitions.toScala)

  override def onPartitionsLost(partitions: JavaCollection[JavaTopicPartition]) =
    listener.onPartitionsLost(consumer, partitions.toScala)

}
