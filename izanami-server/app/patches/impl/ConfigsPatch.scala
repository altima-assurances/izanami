package patches.impl

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import domains.{GlobalContext, errors}
import domains.config.Config.ConfigKey
import domains.config.{Config, ConfigContext, ConfigService}
import domains.events.Events.IzanamiEvent
import env._
import libs.database.Drivers
import patches.PatchInstance
import libs.logs.IzanamiLogger
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import domains.errors.ValidationError
import store.{JsonDataStore, Query}
import store.memorywithdb.CacheEvent
import zio.{RIO, Task, ZIO}

private[impl] case class OldConfig(id: ConfigKey, value: String)

private[impl] object OldConfig {
  val format = Json.format[OldConfig]
}

class ConfigsPatch(
    izanamiConfig: IzanamiConfig,
    drivers: Drivers,
    applicationLifecycle: ApplicationLifecycle
)(implicit system: ActorSystem)
    extends PatchInstance {

  implicit val materializer: Materializer = ActorMaterializer()

  override def patch(): RIO[GlobalContext, Done] = {

    val conf: DbDomainConfig = izanamiConfig.config.db
    IzanamiLogger.info(s"Patch for configs starting for DB ${conf.`type`}")

    // format: off
    lazy val jsonDataStore = JsonDataStore(drivers, izanamiConfig, conf, Flow[IzanamiEvent].mapConcat(_ => List.empty[CacheEvent]), applicationLifecycle)
    // format: on
    IzanamiLogger.info(
      s"Patch for configs starting for DB ${conf.`type`} with ${jsonDataStore.getClass.getSimpleName}"
    )
    for {
      _       <- jsonDataStore.start 
      runtime <- ZIO.runtime[GlobalContext]
      source  <- jsonDataStore.findByQuery(Query.oneOf("*"))
      res     <- Task.fromFuture { _ =>
                    source.map(_._2)
                      .mapAsync(2) { l =>
                        val update: ZIO[ConfigContext, errors.IzanamiErrors, Config] = OldConfig.format.reads(l).fold(
                          { e => ZIO.fromEither(errors.Result.error[Config](ValidationError.fromJsError(e.toSeq.map(t => t.copy(_2 = t._2.toSeq))))) },
                          { config => ConfigService.update(config.id, config.id, Config(config.id, Json.parse(config.value))) }
                        )
                        runtime.unsafeRunToFuture(update.either)
                      }
                      .runWith(Sink.foreach {
                        case Right(e) => IzanamiLogger.debug(s"Config updated with success => $e")
                        case Left(e) => IzanamiLogger.debug(s"Config update failure $e")
                      })
                  }
    } yield res
  }
}
