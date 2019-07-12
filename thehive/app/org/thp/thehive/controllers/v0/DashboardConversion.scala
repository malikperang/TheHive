package org.thp.thehive.controllers.v0

import scala.language.implicitConversions
import scala.util.{Failure, Success}

import play.api.libs.json.Json

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.FString
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.{InvalidFormatAttributeError, Output}
import org.thp.thehive.dto.v0.{InputDashboard, OutputDashboard}
import org.thp.thehive.models.Dashboard
import org.thp.thehive.services.{DashboardSrv, DashboardSteps}

object DashboardConversion {
  implicit def toOutputDashboard(dashboard: Dashboard with Entity): Output[OutputDashboard] =
    Output[OutputDashboard](
      dashboard
        .asInstanceOf[Dashboard]
        .into[OutputDashboard]
        .withFieldConst(_.id, dashboard._id)
        .withFieldConst(_._id, dashboard._id)
        .withFieldComputed(_.status, d => if (d.shared) "Shared" else "Private")
        .withFieldConst(_._type, "dashboard")
        .withFieldConst(_.updatedAt, dashboard._updatedAt)
        .withFieldConst(_.updatedBy, dashboard._updatedBy)
        .withFieldConst(_.createdAt, dashboard._createdAt)
        .withFieldConst(_.createdBy, dashboard._createdBy)
        .transform
    )

  implicit def fromInputDashboard(inputDashboard: InputDashboard): Dashboard =
    inputDashboard
      .into[Dashboard]
      .withFieldComputed(_.shared, _.status == "Shared")
      .transform

  def dashboardProperties(dashboardSrv: DashboardSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[DashboardSteps]
      .property[String]("title")(_.simple.updatable)
      .property[String]("description")(_.simple.updatable)
      .property[String]("definition")(_.simple.updatable)
      .property[String]("status")(_.derived(_.value[Boolean]("shared").map(shared => if (shared) "Shared" else "Private")).custom[String] {
        case (_, _, "Shared", vertex, _, graph, authContext) =>
          dashboardSrv.get(vertex)(graph).share(authContext)
          Success(Json.obj("status" -> "Shared"))
        case (_, _, "Private", vertex, _, graph, authContext) =>
          dashboardSrv.get(vertex)(graph).unshare(authContext)
          Success(Json.obj("status" -> "Private"))
        case (_, _, "Deleted", vertex, _, graph, authContext) =>
          dashboardSrv.get(vertex)(graph).delete(authContext)
          Success(Json.obj("status" -> "Deleted"))
        case (_, _, status, _, _, _, _) =>
          Failure(InvalidFormatAttributeError("status", "String", Set("Shared", "Private", "Deleted"), FString(status)))
      })
      .build
}