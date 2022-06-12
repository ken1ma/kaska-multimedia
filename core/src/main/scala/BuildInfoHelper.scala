package jp.ken1ma.kaska.multimedia
package core

import java.time.{Instant, ZoneId}
import java.time.temporal.ChronoUnit.MINUTES

trait BuildInfoHelper:
  def builtAtMillis: Long

  def buildAtInstant = Instant.ofEpochMilli(builtAtMillis)
  def buildAtDefaultZone = buildAtInstant.atZone(ZoneId.systemDefault)
  def buildAtDefaultOffset = buildAtDefaultZone.toOffsetDateTime

  def buildMinuteAtDefaultOffset = buildAtDefaultOffset.truncatedTo(MINUTES)
