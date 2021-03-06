package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import controllers.actions.SecuredAuthContext
import controllers.dto.meta.Metadata
import controllers.dto.script.GlobalScriptListResult
import domains.script._
import domains.{Import, ImportData, IsAllowed, Key, PatternRights}
import libs.patch.Patch
import libs.ziohelper.JsResults.jsResultToHttpResponse
import play.api.http.HttpEntity
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc._
import store.Query
import controllers.dto.error.ApiErrors
import zio.{IO, Runtime, ZIO}

class GlobalScriptController(
    system: ActorSystem,
    AuthAction: ActionBuilder[SecuredAuthContext, AnyContent],
    cc: ControllerComponents
)(implicit R: Runtime[GlobalScriptContext])
    extends AbstractController(cc) {

  import system.dispatcher
  import libs.http._

  implicit val materializer = ActorMaterializer()(system)

  def list(pattern: String, name_only: Option[Boolean], page: Int = 1, nbElementPerPage: Int = 15): Action[Unit] =
    AuthAction.asyncTask[GlobalScriptContext](parse.empty) { ctx =>
      import GlobalScriptInstances._
      val query: Query = Query.oneOf(ctx.authorizedPatterns).and(pattern.split(",").toList)
      GlobalScriptService
        .findByQuery(query, page, nbElementPerPage)
        .map { r =>
          name_only match {
            case Some(true) =>
              Ok(
                GlobalScriptListResult.partialFormat
                  .writes(
                    GlobalScriptListResult(r.results.toList, Metadata(page, nbElementPerPage, r.count, r.nbPages))
                  )
              )
            case _ =>
              Ok(
                GlobalScriptListResult.format
                  .writes(
                    GlobalScriptListResult(r.results.toList, Metadata(page, nbElementPerPage, r.count, r.nbPages))
                  )
              )
          }
        }
    }

  def create(): Action[JsValue] = AuthAction.asyncZio[GlobalScriptContext](parse.json) { ctx =>
    import GlobalScriptInstances._
    val body = ctx.request.body
    for {
      script <- jsResultToHttpResponse(body.validate[GlobalScript])
      _      <- GlobalScriptService.create(script.id, script).mapError { ApiErrors.toHttpResult }
    } yield Created(Json.toJson(script))
  }

  def get(id: String): Action[Unit] = AuthAction.asyncZio[GlobalScriptContext](parse.empty) { ctx =>
    import GlobalScriptInstances._
    val key = Key(id)
    for {
      mayBeScript  <- GlobalScriptService.getById(key).mapError { ApiErrors.toHttpResult }
      globalScript <- ZIO.fromOption(mayBeScript).mapError(_ => NotFound)
    } yield Ok(Json.toJson(globalScript))
  }

  def update(id: String): Action[JsValue] =
    AuthAction.asyncZio[GlobalScriptContext](parse.json) { ctx =>
      import GlobalScriptInstances._
      val body = ctx.request.body
      for {
        script <- jsResultToHttpResponse(body.validate[GlobalScript])
        _      <- GlobalScriptService.update(Key(id), script.id, script).mapError { ApiErrors.toHttpResult }
      } yield Ok(Json.toJson(script))
    }

  def patch(id: String): Action[JsValue] =
    AuthAction.asyncZio[GlobalScriptContext](parse.json) { ctx =>
      import GlobalScriptInstances._
      val key = Key(id)
      for {
        mayBeScript <- GlobalScriptService.getById(key).mapError { ApiErrors.toHttpResult }
        current     <- ZIO.fromOption(mayBeScript).mapError(_ => NotFound)
        body        = ctx.request.body
        updated     <- jsResultToHttpResponse(Patch.patch(body, current))
        _           <- GlobalScriptService.update(key, current.id, updated).mapError { ApiErrors.toHttpResult }
      } yield Ok(Json.toJson(updated))
    }

  def delete(id: String): Action[AnyContent] = AuthAction.asyncZio[GlobalScriptContext] { ctx =>
    import GlobalScriptInstances._
    val key = Key(id)
    for {
      mayBeScript <- GlobalScriptService.getById(key).mapError { ApiErrors.toHttpResult }
      script      <- ZIO.fromOption(mayBeScript).mapError(_ => NotFound)
      _           <- GlobalScriptService.delete(key).mapError { ApiErrors.toHttpResult }
    } yield Ok(Json.toJson(script))
  }

  def deleteAll(pattern: String): Action[AnyContent] =
    AuthAction.asyncZio[GlobalScriptContext] { ctx =>
      val query: Query = Query.oneOf(ctx.authorizedPatterns).and(pattern.split(",").toList)
      GlobalScriptService
        .deleteAll(query)
        .mapError { ApiErrors.toHttpResult }
        .map(_ => Ok)
    }

  def download(): Action[AnyContent] = AuthAction.asyncTask[GlobalScriptContext] { ctx =>
    import GlobalScriptInstances._
    val query: Query = Query.oneOf(ctx.authorizedPatterns)
    GlobalScriptService
      .findByQuery(query)
      .map { source =>
        val s = source
          .map { case (_, data) => Json.toJson(data) }
          .map(Json.stringify)
          .intersperse("", "\n", "\n")
          .map(ByteString.apply)
        Result(
          header = ResponseHeader(200, Map("Content-Disposition" -> "attachment", "filename" -> "scripts.dnjson")),
          body = HttpEntity.Streamed(s, None, Some("application/json"))
        )
      }

  }

  def upload(strStrategy: String) = AuthAction.asyncTask[GlobalScriptContext](Import.ndJson) { ctx =>
    ImportData.importHttp(strStrategy, ctx.body, GlobalScriptService.importData)
  }

  case class DebugScript(context: JsObject, script: Script)
  object DebugScript {

    implicit val format = {
      import domains.script.ScriptInstances._
      Json.format[DebugScript]
    }
  }

  def debug() = AuthAction.asyncZio[GlobalScriptContext](parse.json) { ctx =>
    import domains.script.ScriptInstances._
    import domains.script.syntax._
    // format: off
    val body = ctx.request.body
    for {
      debugScript                  <- jsResultToHttpResponse(DebugScript.format.reads(body))
      DebugScript(context, script) = debugScript
      execution                    <- script.run(context).mapError(_ => InternalServerError(""))
    } yield Ok(Json.toJson(execution))
    // format: on
  }

}
