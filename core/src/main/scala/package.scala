package jp.ken1ma.kaska.multimedia

import cps.{CpsAwaitable, CpsMonadContext}

package object core:
  // await extension based on
  // https://github.com/rssh/dotty-cps-async/blob/master/shared/src/main/scala/cps/syntax/package.scala
  // which uses `!` but we don't want to be cryptic
  extension [F[_], A ,G[_]](ft: F[A])(using CpsAwaitable[F], CpsMonadContext[G])
    transparent inline def await: A = cps.await[F, A, G](ft)
