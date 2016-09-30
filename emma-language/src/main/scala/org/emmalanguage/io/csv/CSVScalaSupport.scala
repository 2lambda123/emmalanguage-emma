/*
 * Copyright © 2014 TU Berlin (emma@dima.tu-berlin.de)
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
package org.emmalanguage
package io.csv

import io.{Format, ScalaSupport}

import au.com.bytecode.opencsv.{CSVReader, CSVWriter}
import resource._

import scala.language.experimental.macros

import java.io._
import java.net.URI

/** A [[ScalaSupport]] implementation for the [[CSV]] [[Format]]. */
class CSVScalaSupport[A: CSVConverter](override val format: CSV) extends ScalaSupport[A, CSV] {

  override private[emmalanguage] def read(path: String): TraversableOnce[A] =
    new Traversable[A] {

      val conv = implicitly[CSVConverter[A]]

      override def foreach[U](f: (A) => U): Unit =
        for {
          inp <- managed(inpStream(new URI(path)))
          bis <- managed(new BufferedInputStream(inp))
          isr <- managed(new InputStreamReader(bis, format.charset))
          csv <- managed(new CSVReader(isr, format.delimiter, quoteChar(format), escapeChar(format)))
        } {
          var r = csv.readNext()
          while (r != null) {
            f(conv.from(r))
            r = csv.readNext()
          }
        }
    }

  override private[emmalanguage] def write(path: String)(xs: Traversable[A]): Unit =
    for {
      out <- managed(outStream(new URI(path)))
      bos <- managed(new BufferedOutputStream(out))
      osw <- managed(new OutputStreamWriter(bos, format.charset))
      csv <- managed(new CSVWriter(osw, format.delimiter, quoteChar(format), escapeChar(format)))
    } {

      val conv = implicitly[CSVConverter[A]]

      for (x <- xs) {
        val r = conv.to(x)
        csv.writeNext(r)
      }
    }

  // ---------------------------------------------------------------------------
  // Helper functions for reading
  // ---------------------------------------------------------------------------

  private def quoteChar(format: CSV): Char =
    format.quote.getOrElse(CSVWriter.NO_QUOTE_CHARACTER)

  private def escapeChar(format: CSV): Char =
    format.escape.getOrElse(CSVWriter.NO_ESCAPE_CHARACTER)
}

/** Companion object. */
object CSVScalaSupport {

  def apply[A: CSVConverter](format: CSV): CSVScalaSupport[A] =
    new CSVScalaSupport[A](format)
}