package izanami.javadsl

import java.lang
import java.util.function
import java.util.function.Consumer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.stream.javadsl.{AsPublisher, Sink, Source}
import io.vavr.{Function0, Tuple, Tuple2}
import io.vavr.collection.List
import io.vavr.concurrent.Future
import io.vavr.control.{Option, Try}
import izanami._
import org.reactivecouchbase.json.{JsObject, JsValue, Json}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import java.time.LocalDateTime
import java.time.LocalTime

object IzanamiClient {

  /**
   * Create an instance of IzanamiClient.
   *
   * {{{
   *   IzanamiClient client = IzanamiClient.client(
   *           actorSystem,
   *           ClientConfig
   *                   .create(host)
   *                   .withClientId(clientId)
   *                   .withClientSecret(clientSecret)
   *                   .withClientIdHeaderName("Izanami-Client-Id")
   *                   .withClientSecretHeaderName("Izanami-Client-Secret")
   *                   .withDispatcher("izanami-example.blocking-io-dispatcher")
   *                   .sseBackend()
   *   );
   * }}}
   * @param actorSystem
   * @param config
   * @return
   */
  def client(actorSystem: ActorSystem, config: ClientConfig): IzanamiClient =
    new IzanamiClient(config, izanami.scaladsl.IzanamiClient(config)(actorSystem))
}

/**
 * The Izanami client is the root class that allow you to create [[izanami.javadsl.ConfigClient]], [[izanami.javadsl.FeatureClient]] and [[izanami.javadsl.ExperimentsClient]]
 * @param clientConfig
 * @param underlyingClient
 */
class IzanamiClient(clientConfig: ClientConfig, underlyingClient: izanami.scaladsl.IzanamiClient) {

  import underlyingClient._

  /**
   * Get a featureClient instance to interact with features.
   *
   * For example :
   *
   * {{{
   *   FeatureClient featureClient = izanamiClient.featureClient(
   *         Strategies.smartCacheWithSseStrategy("mytvshows:*")
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy, Strategies.fetchWithCacheStrategy, Strategies.smartCacheWithPollingStrategy, Strategies.smartCacheWithSseStrategy
   * @return the [[izanami.javadsl.FeatureClient]]
   */
  def featureClient(strategy: izanami.Strategy): FeatureClient =
    featureClient(strategy, Features.features())

  /**
   * Get a featureClient instance to interact with features.
   *
   * For example :
   *
   * {{{
   *   FeatureClient featureClient = izanamiClient.featureClient(
   *        Strategies.smartCacheWithSseStrategy("mytvshows:*"),
   *        Features.parseJson(environment.getProperty("izanami.fallback.features"))
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy, Strategies.fetchWithCacheStrategy, Strategies.smartCacheWithPollingStrategy, Strategies.smartCacheWithSseStrategy
   * @param fallback: a fallback in case of the server is not available
   * @return the [[izanami.javadsl.FeatureClient]]
   */
  def featureClient(strategy: izanami.Strategy, fallback: ClientConfig => Features): FeatureClient =
    new FeatureClient(underlyingClient.actorSystem,
                      underlyingClient.featureClient(strategy, fallback.andThen(_.underlying)))

  /**
   * Get a featureClient instance to interact with features.
   *
   * For example :
   *
   * {{{
   *   FeatureClient featureClient = izanamiClient.featureClient(
   *        Strategies.smartCacheWithSseStrategy("mytvshows:*"),
   *        Features.parseJson(environment.getProperty("izanami.fallback.features"))
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy, Strategies.fetchWithCacheStrategy, Strategies.smartCacheWithPollingStrategy, Strategies.smartCacheWithSseStrategy
   * @param fallback: a fallback in case of the server is not available
   * @param autocreate enable config autocreation taking value from fallback
   * @return the [[izanami.javadsl.FeatureClient]]
   */
  def featureClient(strategy: izanami.Strategy,
                    fallback: ClientConfig => Features,
                    autocreate: Boolean): FeatureClient =
    new FeatureClient(underlyingClient.actorSystem,
                      underlyingClient.featureClient(strategy, fallback.andThen(_.underlying), autocreate = autocreate))

  /**
   *
   * Get a configClient instance to interact with shared config.
   *
   * For example :
   *
   * {{{
   *   ConfigClient configClient = izanamiClient.configClient(
   *      Strategies.smartCacheWithSseStrategy("mytvshows:*")
   *   );
   * }}}
   * @param strategy: Strategies.dev, Strategies.fetchStrategy, Strategies.fetchWithCacheStrategy, Strategies.smartCacheWithPollingStrategy, Strategies.smartCacheWithSseStrategy
   * @return the [[izanami.javadsl.ConfigClient]]
   */
  def configClient(strategy: izanami.Strategy): ConfigClient =
    new ConfigClient(
      underlyingClient.actorSystem,
      clientConfig,
      underlyingClient.configClient(strategy, izanami.scaladsl.Configs(Seq.empty))
    )

  /**
   * Get a configClient instance to interact with shared config.
   *
   * For example :
   *
   * {{{
   *   ConfigClient configClient = izanamiClient.configClient(
   *      Strategies.smartCacheWithSseStrategy("mytvshows:*"),
   *      Configs.parseJson(environment.getProperty("izanami.fallback.configs"))
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy, Strategies.fetchWithCacheStrategy, Strategies.smartCacheWithPollingStrategy, Strategies.smartCacheWithSseStrategy
   * @param fallback: a fallback in case of the server is not available
   * @return the [[izanami.javadsl.ConfigClient]]
   */
  def configClient(strategy: izanami.Strategy, fallback: Configs): ConfigClient =
    new ConfigClient(underlyingClient.actorSystem,
                     clientConfig,
                     underlyingClient.configClient(strategy, fallback.underlying))

  /**
   * Get a configClient instance to interact with shared config.
   *
   * For example :
   *
   * {{{
   *   ConfigClient configClient = izanamiClient.configClient(
   *      Strategies.smartCacheWithSseStrategy("mytvshows:*"),
   *      Configs.parseJson(environment.getProperty("izanami.fallback.configs"))
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy, Strategies.fetchWithCacheStrategy, Strategies.smartCacheWithPollingStrategy, Strategies.smartCacheWithSseStrategy
   * @param fallback: a fallback in case of the server is not available
   * @param autocreate enable config autocreation taking value from fallback
   * @return the [[izanami.javadsl.ConfigClient]]
   */
  def configClient(strategy: izanami.Strategy, fallback: Configs, autocreate: Boolean): ConfigClient =
    new ConfigClient(underlyingClient.actorSystem,
                     clientConfig,
                     underlyingClient.configClient(strategy, fallback.underlying, autocreate = autocreate))

  /**
   * Get a experimentClient instance to interact with experiments.
   *
   * For example :
   *
   * {{{
   *   ExperimentsClient experimentsClient = izanamiClient.experimentClient(
   *     Strategies.fetchStrategy()
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy
   * @return the [[izanami.javadsl.ExperimentsClient]]
   */
  def experimentClient(strategy: izanami.Strategy): ExperimentsClient =
    new ExperimentsClient(underlyingClient.experimentClient(strategy))

  /**
   * Get a experimentClient instance to interact with experiments.
   *
   * For example :
   *
   * {{{
   *   ExperimentsClient experimentsClient = izanamiClient.experimentClient(
   *     Strategies.fetchStrategy(),
   *     Experiments.parseJson(environment.getProperty("izanami.fallback.experiments"))
   *   );
   * }}}
   *
   * @param strategy: Strategies.dev, Strategies.fetchStrategy
   * @param fallback: a fallback in case of the server is not available
   * @return the [[izanami.javadsl.ExperimentsClient]]
   */
  def experimentClient(strategy: izanami.Strategy, fallback: izanami.Experiments): ExperimentsClient =
    new ExperimentsClient(underlyingClient.experimentClient(strategy, fallback))

  /**
   * Create a proxy to expose a part of the api of the server from your app.
   *
   * {{{
   *   Proxy proxy = izanamiClient.proxy()
   *           .withFeaturePattern("mytvshows:*")
   *           .withFeatureClient(featureClient)
   *           .withExperimentPattern("mytvshows:*")
   *           .withExperimentsClient(experimentClient);
   * }}}
   * @return the [[izanami.javadsl.Proxy]]
   */
  def proxy(): Proxy = new Proxy(underlyingClient.proxy())

  /**
   * Create a proxy to expose a part of the api of the server from your app.
   *
   * @param featureClient [[izanami.javadsl.FeatureClient]]
   * @param configClient [[izanami.javadsl.ConfigClient]]
   * @param experimentClient [[izanami.javadsl.ExperimentsClient]]
   * @return the [[izanami.javadsl.Proxy]]
   */
  def proxy(featureClient: FeatureClient, configClient: ConfigClient, experimentClient: ExperimentsClient): Proxy =
    new Proxy(
      underlyingClient.proxy(
        featureClient.underlying,
        configClient.underlying,
        experimentClient.underlying
      )
    )

}

///////////////////////////////////////////////////////////////////////
////////////////////////////   Proxy   ////////////////////////////////
///////////////////////////////////////////////////////////////////////

case class Proxy(underlying: izanami.scaladsl.Proxy)(implicit actorSystem: ActorSystem,
                                                     izanamiDispatcher: IzanamiDispatcher) {

  import izanamiDispatcher._
  import JsonConv._
  import Vavr._

  def withFeatureClient(featureClient: FeatureClient) =
    this.copy(underlying = underlying.withFeatureClient(featureClient.underlying))
  def withConfigClient(configClient: ConfigClient) =
    this.copy(underlying = underlying.withConfigClient(configClient.underlying))
  def withExperimentsClient(experimentsClient: ExperimentsClient) =
    this.copy(underlying = underlying.withExperimentsClient(experimentsClient.underlying))
  @annotation.varargs
  def withFeaturePattern(pattern: String*) =
    this.copy(underlying = underlying.withFeaturePattern(pattern: _*))
  @annotation.varargs
  def withConfigPattern(pattern: String*) =
    this.copy(underlying = underlying.withFeaturePattern(pattern: _*))
  @annotation.varargs
  def withExperimentPattern(pattern: String*) =
    this.copy(underlying = underlying.withExperimentPattern(pattern: _*))

  def statusAndJsonResponse(context: JsObject, userId: String): Future[Tuple2[Integer, JsValue]] =
    statusAndJsonResponse(Option.some(context), Option.some(userId))

  def statusAndJsonResponse(): Future[Tuple2[Integer, JsValue]] =
    statusAndJsonResponse(Option.none[JsObject](), Option.none[String]())

  def statusAndJsonResponse(context: Option[JsObject], userId: Option[String]): Future[Tuple2[Integer, JsValue]] = {
    underlying
      .statusAndJsonResponse(
        context.toScala().map(_.toScala().as[play.api.libs.json.JsObject]),
        userId.toScala()
      )
      .map {
        case (status, json) => Tuple.of(Int.box(status), json.toJava())
      }
  }.toJava

  def statusAndStringResponse(context: JsObject, userId: String): Future[Tuple2[Integer, String]] =
    statusAndStringResponse(Option.some(context), Option.some(userId))

  def statusAndStringResponse(): Future[Tuple2[Integer, String]] =
    statusAndStringResponse(Option.none[JsObject](), Option.none[String]())

  def statusAndStringResponse(context: Option[JsObject], userId: Option[String]): Future[Tuple2[Integer, String]] =
    statusAndJsonResponse(context, userId).map {
      new function.Function[Tuple2[Integer, JsValue], Tuple2[Integer, String]] {
        override def apply(t: Tuple2[Integer, JsValue]): Tuple2[Integer, String] =
          Tuple.of(t._1, Json.stringify(t._2))
      }
    }

  def markVariantDisplayed(experimentId: String, clientId: String): Future[Tuple2[Integer, JsValue]] =
    underlying
      .markVariantDisplayed(experimentId, clientId)
      .map {
        case (status, json) => Tuple.of(Int.box(status), json.toJava())
      }
      .toJava
  def markVariantDisplayedStringResponse(experimentId: String, clientId: String): Future[Tuple2[Integer, String]] =
    underlying
      .markVariantDisplayedStringResp(experimentId, clientId)
      .map {
        case (status, resp) => Tuple.of(Int.box(status), resp)
      }
      .toJava

  def markVariantWon(experimentId: String, clientId: String): Future[Tuple2[Integer, JsValue]] =
    underlying
      .markVariantWon(experimentId, clientId)
      .map {
        case (status, json) => Tuple.of(Int.box(status), json.toJava())
      }
      .toJava
  def markVariantWonStringResponse(experimentId: String, clientId: String): Future[Tuple2[Integer, String]] =
    underlying
      .markVariantWonStringResp(experimentId, clientId)
      .map {
        case (status, resp) => Tuple.of(Int.box(status), resp)
      }
      .toJava

}
///////////////////////////////////////////////////////////////////////
/////////////////////////   Features   ////////////////////////////////
///////////////////////////////////////////////////////////////////////

object Features {

  @annotation.varargs
  def features(features: Feature*): ClientConfig => Features = { clientConfig =>
    Features(izanami.scaladsl.Features(clientConfig, features, Seq.empty))
  }

  def empty(): ClientConfig => Features = { clientConfig =>
    Features(izanami.scaladsl.Features(clientConfig, Seq.empty, Seq.empty))
  }

  def features(features: java.lang.Iterable[Feature]): ClientConfig => Features = {
    import scala.collection.JavaConverters._
    clientConfig =>
      Features(
        izanami.scaladsl
          .Features(clientConfig, features.asScala.toSeq, Seq.empty)
      )
  }

  def feature(key: String, active: Boolean)                          = DefaultFeature(key, active)
  def defaultFeature(key: String, active: Boolean)                   = DefaultFeature(key, active)
  def releaseDate(key: String, active: Boolean, date: LocalDateTime) = ReleaseDateFeature(key, active, date)
  def dateRange(key: String, active: Boolean, from: LocalDateTime, to: LocalDateTime) =
    DateRangeFeature(key, active, from, to)
  def hourRange(key: String, active: Boolean, startAt: LocalTime, endAt: LocalTime) =
    HourRangeFeature(key, active, None, startAt, endAt)
  def percentage(key: String, active: Boolean, percentage: Int) = PercentageFeature(key, active, None, percentage)
  def script(key: String, active: Boolean, `type`: String, script: String) =
    ScriptFeature(key, active, None, Script(`type`, script))
  def globalScript(key: String, active: Boolean, ref: String) = GlobalScriptFeature(key, active, None, ref)

  def noStrategyType(): FeatureType   = FeatureType.NO_STRATEGY
  def releaseDateType(): FeatureType  = FeatureType.RELEASE_DATE
  def dateRangeType(): FeatureType    = FeatureType.DATE_RANGE
  def hourRangeType(): FeatureType    = FeatureType.HOUR_RANGE
  def percentageType(): FeatureType   = FeatureType.PERCENTAGE
  def scriptType(): FeatureType       = FeatureType.SCRIPT
  def globalScriptType(): FeatureType = FeatureType.GLOBAL_SCRIPT

  def parseJson(json: String): ClientConfig => Features =
    izanami.scaladsl.Features.parseJson(json).andThen(Features.apply _)

}

case class Registration(underlying: izanami.scaladsl.Registration) {
  def onComplete(cb: Try[Done] => Unit): Unit =
    underlying.onComplete {
      case Success(done) => cb.apply(Try.success(done))
      case Failure(e)    => cb.apply(Try.failure(e))
    }
  def close(): Unit = underlying.close()
}

case class Features(underlying: izanami.scaladsl.Features) {
  import JsonConv._
  def isActive(key: String): Boolean =
    underlying.isActive(key)

  def tree(): JsObject =
    underlying.tree().toJava().asObject()

}

class FeatureClient(actorSystem: ActorSystem, val underlying: scaladsl.FeatureClient)(
    implicit izanamiDispatcher: IzanamiDispatcher
) {

  import Vavr._
  import JsonConv._
  import izanamiDispatcher.ec

  /**
   * Create a feature
   * @param id Feature Id
   * @param enabled If this feature is enabled by default or not
   * @param activationStrategy activationStrategy for this feature (@see {@link izanami.FeatureType})
   * @param parameters optional parameters (depends on activationStrategy)
   * @return
   */
  def createJsonFeature(id: String,
                        enabled: Boolean = true,
                        activationStrategy: FeatureType = FeatureType.NO_STRATEGY,
                        parameters: Option[JsObject] = Option.none()): Future[Feature] =
    underlying
      .createJsonFeature(id,
                         enabled,
                         activationStrategy,
                         parameters.toScala().map(_.toScala().as[play.api.libs.json.JsObject]))
      .toJava

  def createFeature(feature: Feature): Future[Feature] =
    underlying
      .createFeature(feature)
      .toJava

  /**
   * Update a feature
   * @param id the previous id of the feature
   * @param feature the feature to update
   * @return
   */
  def updateFeature(id: String, feature: Feature): Future[Feature] =
    underlying.updateFeature(id, feature).toJava

  /**
   * Enabled or disable a feature
   * @param id the id of the feature
   * @param enabled the status to set
   * @return
   */
  def switchFeature(id: String, enabled: Boolean): Future[Feature] =
    underlying.switchFeature(id, enabled).toJava

  /**
   * Delete a feature
   * @param id the id of the feature to delete
   * @return
   */
  def deleteFeature(id: String): Future[Done] =
    underlying
      .deleteFeature(id)
      .map { _ =>
        Done.done()
      }
      .toJava

  /**
   * Get features by pattern like my:keys:*
   * @param pattern
   * @return
   */
  def features(pattern: String): Future[Features] =
    underlying.features(pattern).map(Features.apply _).toJava

  /**
   * Get features by pattern like my:keys:*
   * @param pattern
   * @return
   */
  def features(pattern: io.vavr.collection.Seq[String]): Future[Features] =
    underlying.features(pattern.toScala()).map(Features.apply _).toJava

  /**
   * Get features by pattern like my:keys:* with a json context
   * @param pattern
   * @return
   */
  def features(pattern: String, context: JsObject): Future[Features] =
    underlying
      .features(pattern, context.toScala().as[play.api.libs.json.JsObject])
      .map(Features.apply _)
      .toJava

  /**
   * Get features by pattern like my:keys:* with a json context
   * @param pattern
   * @return
   */
  def features(pattern: io.vavr.collection.Seq[String], context: JsObject): Future[Features] =
    underlying
      .features(pattern.toScala(), context.toScala().as[play.api.libs.json.JsObject])
      .map(Features.apply _)
      .toJava

  /**
   * Return a value if the feature is active or else a default.
   * @param key
   * @param ok
   * @param ko
   * @tparam T
   * @return
   */
  def featureOrElse[T](key: String, ok: Function0[T], ko: Function0[T]): Future[T] =
    checkFeature(key).map {
      new java.util.function.Function[java.lang.Boolean, T] {
        override def apply(t: lang.Boolean): T = t match {
          case java.lang.Boolean.TRUE  => ok.get()
          case java.lang.Boolean.FALSE => ko.get()
        }
      }
    }

  /**
   * Return a value if the feature is active or else a default.
   * @param key
   * @param ok
   * @param ko
   * @tparam T
   * @return
   */
  def featureOrElseAsync[T](key: String, ok: Function0[Future[T]], ko: Function0[Future[T]]): Future[T] =
    checkFeature(key).flatMap {
      new java.util.function.Function[java.lang.Boolean, Future[T]] {
        override def apply(t: lang.Boolean): Future[T] = t match {
          case java.lang.Boolean.TRUE  => ok.get()
          case java.lang.Boolean.FALSE => ko.get()
        }
      }
    }

  /**
   * Return a value if the feature is active or else a default.
   * @param key
   * @param ok
   * @param ko
   * @tparam T
   * @return
   */
  def featureOrElse[T](key: String, context: JsObject, ok: Function0[T], ko: Function0[T]): Future[T] =
    checkFeature(key, context).map {
      new java.util.function.Function[java.lang.Boolean, T] {
        override def apply(t: lang.Boolean): T = t match {
          case java.lang.Boolean.TRUE  => ok.get()
          case java.lang.Boolean.FALSE => ko.get()
        }
      }
    }

  /**
   * Return a value if the feature is active or else a default.
   * @param key
   * @param ok
   * @param ko
   * @tparam T
   * @return
   */
  def featureOrElseAsync[T](key: String,
                            context: JsObject,
                            ok: Function0[Future[T]],
                            ko: Function0[Future[T]]): Future[T] =
    checkFeature(key, context).flatMap {
      new java.util.function.Function[java.lang.Boolean, Future[T]] {
        override def apply(t: lang.Boolean): Future[T] = t match {
          case java.lang.Boolean.TRUE  => ok.get()
          case java.lang.Boolean.FALSE => ko.get()
        }
      }
    }

  /**
   * Check if a feature is active or not
   * @param key the id of the feature
   * @return
   */
  def checkFeature(key: String): Future[java.lang.Boolean] =
    underlying.checkFeature(key).map(Boolean.box).toJava

  /**
   * Check if a feature is active or not using a json context
   * @param key
   * @param context
   * @return
   */
  def checkFeature(key: String, context: JsObject): Future[java.lang.Boolean] =
    underlying
      .checkFeature(key, context.toScala().as[play.api.libs.json.JsObject])
      .map(Boolean.box)
      .toJava

  /**
   * Register a callback to be notified when a feature change.
   * @param key
   * @param cb
   * @return
   */
  def onFeatureChanged(key: String)(cb: Consumer[Feature]): Registration =
    Registration(underlying.onFeatureChanged(key) { f =>
      cb.accept(f)
      ()
    })

  /**
   * Register a callback to be notified of events concerning features.
   * @param pattern
   * @param cb
   * @return
   */
  def onEvent(pattern: String)(cb: Consumer[FeatureEvent]): Registration =
    Registration(underlying.onEvent(pattern) { f =>
      cb.accept(f)
      ()
    })

  /**
   * Get a Akka stream source of events
   * @param pattern a pattern to filter events
   * @return
   */
  def featuresSource(pattern: String): Source[FeatureEvent, NotUsed] =
    underlying.featuresSource(pattern).asJava

  /**
   * Get a ReactiveStream publisher of events
   * @param pattern a pattern to filter events
   * @return
   */
  def featuresStream(pattern: String): Publisher[FeatureEvent] =
    underlying.featuresStream(pattern)

}

///////////////////////////////////////////////////////////////////////
//////////////////////////   Configs   ////////////////////////////////
///////////////////////////////////////////////////////////////////////

object Config {
  import JsonConv._
  def config(id: String, value: JsValue): Config =
    Config(izanami.scaladsl.Config(id, value.toScala()))
}

case class Config(underlying: izanami.scaladsl.Config) {
  import JsonConv._
  def value(): JsValue = underlying.value.toJava()
  def id(): String     = underlying.id
}

object Configs {
  import JsonConv._
  import scala.collection.JavaConverters._

  @annotation.varargs
  def configs(configs: Config*): Configs = Configs(
    izanami.scaladsl.Configs(
      configs.map(
        c =>
          izanami.scaladsl
            .Config(c.id(), c.value().toScala())
      )
    )
  )

  def configs(configs: java.lang.Iterable[Config]): Configs =
    Configs(
      izanami.scaladsl.Configs(
        configs.asScala.toSeq
          .map(c => izanami.scaladsl.Config(c.id(), c.value().toScala()))
      )
    )

  def empty(): Configs = Configs(
    izanami.scaladsl.Configs(
      Seq.empty
    )
  )

  def config(id: String, value: JsValue): Config = Config.config(id, value)

  def parseJson(jsonStr: String): Configs =
    Configs(izanami.scaladsl.Configs.parseJson(jsonStr))
}

case class Configs(underlying: izanami.scaladsl.Configs) {
  import JsonConv._
  def config(key: String) = underlying.get(key).toJava()
  def tree(): JsObject    = underlying.tree().toJava().asObject()
}

sealed trait ConfigEvent {
  def id: String
}

object ConfigEvent {
  case class ConfigCreated(id: String, config: Config)                    extends ConfigEvent
  case class ConfigUpdated(id: String, config: Config, oldConfig: Config) extends ConfigEvent
  case class ConfigDeleted(id: String)                                    extends ConfigEvent
}

class ConfigClient(actorSystem: ActorSystem, clientConfig: ClientConfig, val underlying: izanami.scaladsl.ConfigClient)(
    implicit izanamiDispatcher: IzanamiDispatcher
) {

  import Vavr._
  import JsonConv._
  import ConfigEvent._

  import izanamiDispatcher.ec

  private val materializer =
    ActorMaterializer(ActorMaterializerSettings(actorSystem).withDispatcher(clientConfig.dispatcher))(actorSystem)

  /**
   * Create a config
   * @param config the config to create
   */
  def createConfig(config: Config): Future[Config] =
    underlying
      .createConfig(config.underlying)
      .map { Config.apply }
      .toJava

  /**
   * Create a config
   * @param id the id of the config to create
   * @param config a json value of the config
   */
  def createConfig(id: String, config: JsValue): Future[JsValue] =
    underlying
      .createConfig(id, config.toScala())
      .map { _.toJava }
      .toJava

  /**
   * Update a config with an id and a config. If the id has changed, the id in param should be the old value.
   * @param id the new id of the config
   * @param config a config with its id
   */
  def updateConfig(id: String, config: Config): Future[Config] =
    underlying
      .updateConfig(id, config.underlying)
      .map { Config.apply }
      .toJava

  /**
   * Update a config with an id and a Json value
   * There is an oldId and a new id if the id has changed. In the other cases it should be the same value.
   * @param oldId the previous id of the config
   * @param id the new id of the config
   * @param config a json value of the config
   */
  def updateConfig(oldId: String, id: String, config: JsValue): Future[JsValue] =
    underlying
      .updateConfig(oldId, id, config.toScala())
      .map { _.toJava }
      .toJava

  /**
   * Delete a config by id.
   * @param id the id of the config to delete
   */
  def deleteConfig(id: String): Future[Done] =
    underlying
      .deleteConfig(id)
      .map { _ =>
        Done.done()
      }
      .toJava

  /**
   * Get configs for a pattern like my:keys:*
   * @param pattern
   * @return
   */
  def configs(pattern: String = "*"): Future[Configs] =
    underlying
      .configs(pattern)
      .map { c =>
        Configs(c)
      }
      .toJava

  /**
   * Get configs for a pattern like my:keys:*
   * @param pattern
   * @return
   */
  def configs(pattern: io.vavr.collection.Seq[String]): Future[Configs] =
    underlying
      .configs(pattern.toScala())
      .map { c =>
        Configs(c)
      }
      .toJava

  /**
   * Get a config by his key.
   * @param key
   * @return a json value. Empty if the config is not set.
   */
  def config(key: String): Future[JsValue] =
    underlying
      .config(key)
      .map { c =>
        c.toJava()
      }
      .toJava

  /**
   * Register a callback to be notified when a config change.
   * @param key
   * @param cb
   * @return
   */
  def onConfigChanged(key: String)(cb: Consumer[JsValue]): Registration =
    Registration(
      underlying.onConfigChanged(key)(json => cb.accept(json.toJava()))
    )

  /**
   * Register a callback to be notified of events concerning configs.
   * @param pattern
   * @param cb
   * @return
   */
  def onEvent(pattern: String = "*", cb: Consumer[ConfigEvent]): Registration =
    Registration(
      underlying
        .onEvent(pattern) { c =>
          cb.accept(convertEvent(c))
          ()
        }
    )

  /**
   * Get a Akka stream source of events
   * @param pattern a pattern to filter events
   * @return
   */
  def configsSource(pattern: String): Source[ConfigEvent, NotUsed] =
    underlying
      .configsSource(pattern)
      .map { convertEvent }
      .asJava

  private def convertEvent(evt: izanami.scaladsl.ConfigEvent): ConfigEvent =
    evt match {
      case izanami.scaladsl.ConfigEvent.ConfigCreated(_, id, c) =>
        ConfigCreated(id, Config(c))
      case izanami.scaladsl.ConfigEvent.ConfigUpdated(_, id, c, o) =>
        ConfigUpdated(id, Config(c), Config(o))
      case izanami.scaladsl.ConfigEvent.ConfigDeleted(_, id) => ConfigDeleted(id)
    }

  /**
   * Get a ReactiveStream publisher of events
   * @param pattern a pattern to filter events
   * @return
   */
  def configsStream(pattern: String = "*"): Publisher[ConfigEvent] =
    configsSource(pattern)
      .runWith(Sink.asPublisher(AsPublisher.WITH_FANOUT), materializer)

}

///////////////////////////////////////////////////////////////////////
/////////////////////////   Experiments   /////////////////////////////
///////////////////////////////////////////////////////////////////////

class ExperimentsClient(val underlying: izanami.scaladsl.ExperimentsClient)(
    implicit izanamiDispatcher: IzanamiDispatcher
) {
  import izanamiDispatcher.ec
  import Vavr._
  import scala.collection.JavaConverters._
  import JsonConv._

  /**
   * Get an experiment by id.
   *
   * @param id
   * @return
   */
  def experiment(id: String): Future[Option[ExperimentClient]] =
    underlying
      .experiment(id)
      .map(
        mayBe =>
          mayBe
            .map(e => Option.of(new ExperimentClient(e)))
            .getOrElse(Option.none())
      )
      .toJava

  /**
   * Get experiments by pattern like my:keys:*
   *
   * @param pattern
   * @return
   */
  def list(pattern: io.vavr.collection.Seq[String]): Future[List[ExperimentClient]] =
    underlying
      .list(pattern.toScala())
      .map(exps => List.ofAll(exps.map(e => new ExperimentClient(e)).asJava))
      .toJava

  /**
   * Return experiments and the associated variants as a tree form.
   *
   * @param pattern
   * @param clientId
   * @return
   */
  def tree(pattern: String, clientId: String): Future[JsValue] =
    underlying.tree(pattern, clientId).map(_.toJava()).toJava

  /**
   * Return experiments and the associated variants as a tree form.
   *
   * @param pattern
   * @param clientId
   * @return
   */
  def tree(pattern: io.vavr.collection.Seq[String], clientId: String): Future[JsValue] =
    underlying.tree(pattern.toScala(), clientId).map(_.toJava()).toJava

  /**
   * Return the variant for a client id if exists
   * @param id
   * @param clientId
   * @return
   */
  def getVariantFor(id: String, clientId: String): Future[Option[Variant]] =
    underlying
      .getVariantFor(id, clientId)
      .map(_.map(v => Option.of(v)).getOrElse(Option.none()))
      .toJava

  /**
   * Notify the server that the variant has been displayed for this client id
   * @param id
   * @param clientId
   * @return
   */
  def markVariantDisplayed(id: String, clientId: String): Future[ExperimentVariantDisplayed] =
    underlying.markVariantDisplayed(id, clientId).toJava

  /**
   * Notify the server that the variant has won for this client id
   * @param id
   * @param clientId
   * @return
   */
  def markVariantWon(id: String, clientId: String): Future[ExperimentVariantWon] =
    underlying.markVariantWon(id, clientId).toJava

}

class ExperimentClient(underlying: izanami.scaladsl.ExperimentClient)(implicit izanamiDispatcher: IzanamiDispatcher) {
  import izanamiDispatcher.ec
  import Vavr._

  /**
   * Return the variant for a client id if exists
   * @param clientId
   * @return
   */
  def getVariantFor(clientId: String): Future[Option[Variant]] =
    underlying
      .getVariantFor(clientId)
      .map(_.map(v => Option.of(v)).getOrElse(Option.none()))
      .toJava

  /**
   * Notify the server that the variant has been displayed for this client id
   * @param clientId
   * @return
   */
  def markVariantDisplayed(clientId: String): Future[ExperimentVariantDisplayed] =
    underlying.markVariantDisplayed(clientId).toJava

  /**
   * Notify the server that the variant has won for this client id
   * @param clientId
   * @return
   */
  def markVariantWon(clientId: String): Future[ExperimentVariantWon] =
    underlying.markVariantWon(clientId).toJava

}

private[javadsl] object JsonConv {

  implicit class JsontoJava(json: play.api.libs.json.JsValue) {
    def toJava(): JsValue =
      Json.parse(play.api.libs.json.Json.stringify(json))
  }

  implicit class JsontoScala(json: JsValue) {
    def toScala(): play.api.libs.json.JsValue =
      play.api.libs.json.Json.parse(Json.stringify(json))
  }
}

private[javadsl] object Vavr {

  implicit class ToScalaSeq[T](o: io.vavr.collection.Seq[T]) {
    def toScala(): scala.Seq[T] = {
      import scala.jdk.CollectionConverters._
      o.asJava().asScala.toSeq
    }
  }

  implicit class ToScalaOption[T](o: Option[T]) {
    def toScala(): scala.Option[T] =
      if (o.isDefined) {
        Some(o.get())
      } else {
        None
      }
  }

  implicit class ToJavaFuture[T](f: concurrent.Future[T]) {
    def toJava(implicit ec: ExecutionContext) = {
      val promise = io.vavr.concurrent.Promise.make[T]()
      f.onComplete {
        case Success(s) => promise.trySuccess(s)
        case Failure(e) => promise.tryFailure(e)
      }
      promise.future()
    }
  }
}
