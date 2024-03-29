package com.fakhritdinov.simpleflow.internal

import cats.effect.{Clock, Sync, Timer}
import cats.syntax.all._
import com.fakhritdinov.kafka.TopicPartition
import com.fakhritdinov.simpleflow.Persistence
import com.fakhritdinov.simpleflow.Persistence.{Persisted, Snapshot}

import java.util.concurrent.TimeUnit

private[simpleflow] class PersistenceManager[F[_]: Sync: Timer, K, S](
  persistence: Persistence[F, K, S],
  interval:    Long
) {

  def persist(state0: State[K, S]): F[State[K, S]] =
    for {
      now    <- Clock[F].monotonic(TimeUnit.MILLISECONDS)
      should  = state0.lastPersistTime + interval < now
      state1 <- if (should)
                  for {
                    persisted <- persistence.persist(state0.partitions)
                  } yield persistedState(state0, persisted, now)
                else state0.pure[F]
    } yield state1

  def restore(partitions: Set[TopicPartition]): F[Snapshot[K, S]] =
    persistence.restore(partitions)

  private def persistedState(state: State[K, S], persisted: Persisted[K], now: Long) = {
    val commit = state.polledOffsets.flatMap { case (partition, offsets) =>
      persisted.get(partition).map { persisted =>
        val persistedOffsets = offsets.collect { case (k, o) if persisted contains k => o }
        val offset           = persistedOffsets.minOption.getOrElse(offsets.values.min)
        partition -> offset
      }
    }
    state.copy(
      commitOffsets = state.commitOffsets ++ commit,
      lastPersistTime = now
    )
  }

}
