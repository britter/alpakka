/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.csv

import akka.event.Logging
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

import scala.util.control.NonFatal

/**
 * Internal API: Use [[akka.stream.alpakka.csv.scaladsl.CsvParsing]] instead.
 */
private[csv] class CsvParsingStage(delimiter: Byte, quoteChar: Byte, escapeChar: Byte)
    extends GraphStage[FlowShape[ByteString, List[ByteString]]] {

  private val in = Inlet[ByteString](Logging.simpleName(this) + ".in")
  private val out = Outlet[List[ByteString]](Logging.simpleName(this) + ".out")
  override val shape = FlowShape(in, out)

  override protected def initialAttributes: Attributes = Attributes.name("CsvParsing")

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val buffer = new CsvParser(delimiter, quoteChar, escapeChar)

      setHandlers(in, out, this)

      override def onPush(): Unit = {
        buffer.offer(grab(in))
        tryPollBuffer()
      }

      override def onPull(): Unit =
        tryPollBuffer()

      override def onUpstreamFinish(): Unit =
        buffer.poll(requireLineEnd = false) match {
          case Some(csvLine) ⇒ emit(out, csvLine)
          case _ ⇒ completeStage()
        }

      private def tryPollBuffer() =
        try buffer.poll(requireLineEnd = true) match {
          case Some(csvLine) ⇒ push(out, csvLine)
          case _ ⇒ if (isClosed(in)) completeStage() else pull(in)
        } catch {
          case NonFatal(ex) ⇒ failStage(ex)
        }
    }
}
