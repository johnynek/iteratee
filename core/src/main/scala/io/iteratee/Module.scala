package io.iteratee

import cats.Monad

/**
 * @groupname Syntax Extension methods
 * @groupprio Syntax 3
 */
trait Module[F[_]] {
  type M[f[_]] <: Monad[f]

  protected def F: M[F]

  /**
   * @group Syntax
   */
  final object syntax {
    final implicit class EffectfulValueOps[A](fa: F[A]) {
      final def intoEnumerator: Enumerator[F, A] = Enumerator.liftM(fa)(F)
      final def intoIteratee[E]: Iteratee[F, E, A] = Iteratee.liftM(fa)(F)
    }
  }
}
