package com.fakhritdinov.simpleflow

import cats.Applicative
import cats.syntax.all._
import com.fakhritdinov.kafka.TopicPartition
import com.fakhritdinov.simpleflow.Persistence._

/** Persistence API should provide functionality
  * for storing current state on partition revoked
  * and restoring state on partition joined.
  *
  * Current state represented by `Snapshot`.
  *
  * @tparam F effect
  * @tparam K key
  * @tparam S state
  */
trait Persistence[F[_], K, S] {

  /** Persist current state.
    *
    * Implementation NOT required to persist all states from snapshot
    * and must return actual persisted keys.
    *
    * @param snapshot current state by partition
    * @return persisted keys by partition
    */
  def persist(snapshot: Snapshot[K, S]): F[Persisted[K]]

  def restore(partitions: Set[TopicPartition]): F[Snapshot[K, S]]

}

object Persistence {

  type Snapshot[K, S] = Map[TopicPartition, Map[K, S]]
  type Persisted[K]   = Map[TopicPartition, Set[K]]

  def empty[F[_]: Applicative, K, S] = new Persistence[F, K, S] {
    def persist(state:      Snapshot[K, S])      = Map.empty[TopicPartition, Set[K]].pure[F]
    def restore(partitions: Set[TopicPartition]) = Map.empty[TopicPartition, Map[K, S]].pure[F]
  }

}
