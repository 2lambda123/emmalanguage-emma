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
package eu.stratosphere.emma.examples

import eu.stratosphere.emma.api.model._


package object graphs {

  // --------------------------------------------------------------------------
  // Schema
  // --------------------------------------------------------------------------

  case class VertexWithLabel[VT, LT](@id id: VT, label: LT) extends Identity[VT] {
    def identity = id
  }

  case class Edge[VT](@id src: VT, @id dst: VT) extends Identity[Edge[VT]] {
    def identity = Edge(src, dst)
  }

  case class EdgeWithLabel[VT, LT](@id src: VT, @id dst: VT, label: LT) extends Identity[Edge[VT]] {
    def identity = Edge(src, dst)
  }

}
