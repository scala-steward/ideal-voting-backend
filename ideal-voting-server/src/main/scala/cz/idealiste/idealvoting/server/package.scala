package cz.idealiste.idealvoting

import zio.{Has, Tag, ZLayer}

import scala.annotation.nowarn

package object server {
  // This is quite an ugly hack :( Thankfully we shouldn't need it in ZIO 2
  private[server] def upcastLayer[O] = new UpcastLayerPartiallyApplied[O]
  @nowarn("msg=it is not recommended to define classes/objects inside of package objects")
  private[server] class UpcastLayerPartiallyApplied[O] {
    def apply[I, E, OSub <: O: Tag](layer: ZLayer[I, E, Has[OSub]])(implicit ev: Tag[O]): ZLayer[I, E, Has[O]] =
      layer.map { r =>
        val osub: O = r.get[OSub]
        r.add(osub)(ev)
      }
  }
}
