package indigo

import indigo.gameengine.GameEngine
import indigo.shared.subsystems.SubSystemsRegister
import indigo.entry.StandardFrameProcessor

// Indigo is Scala.js only at the moment, revisit if/when we go to the JVM
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * A trait representing a minimal set of functions to get your game running
  * @tparam StartUpData The class type representing your successful startup data
  * @tparam Model The class type representing your game's model
  * @tparam ViewModel The class type representing your game's view model
  */
trait IndigoDemo[BootData, StartUpData, Model, ViewModel] extends GameLauncher {

  def eventFilters: EventFilters

  def boot(flags: Map[String, String]): Outcome[BootResult[BootData]]

  def setup(bootData: BootData, assetCollection: AssetCollection, dice: Dice): Outcome[Startup[StartUpData]]

  def initialModel(startupData: StartUpData): Outcome[Model]

  def initialViewModel(startupData: StartUpData, model: Model): Outcome[ViewModel]

  def updateModel(context: FrameContext[StartUpData], model: Model): GlobalEvent => Outcome[Model]

  def updateViewModel(context: FrameContext[StartUpData], model: Model, viewModel: ViewModel): GlobalEvent => Outcome[ViewModel]

  def present(context: FrameContext[StartUpData], model: Model, viewModel: ViewModel): Outcome[SceneUpdateFragment]

  private val subSystemsRegister: SubSystemsRegister =
    new SubSystemsRegister()

  private def indigoGame(bootUp: BootResult[BootData]): GameEngine[StartUpData, Model, ViewModel] = {
    val subSystemEvents = subSystemsRegister.register(bootUp.subSystems.toList)

    val frameProcessor: StandardFrameProcessor[StartUpData, Model, ViewModel] =
      new StandardFrameProcessor(
        subSystemsRegister,
        eventFilters,
        updateModel,
        updateViewModel,
        present
      )

    new GameEngine[StartUpData, Model, ViewModel](
      bootUp.fonts,
      bootUp.animations,
      (ac: AssetCollection) => (d: Dice) => setup(bootUp.bootData, ac, d),
      (sd: StartUpData) => initialModel(sd),
      (sd: StartUpData) => (m: Model) => initialViewModel(sd, m),
      frameProcessor,
      subSystemEvents
    )
  }

  // @SuppressWarnings(Array("org.wartremover.warts.GlobalExecutionContext"))
  final protected def ready(flags: Map[String, String]): Unit =
    boot(flags) match {
      case oe @ Outcome.Error(e, _) =>
        IndigoLogger.error("Error during boot - Halting")
        IndigoLogger.error(oe.reportCrash)
        throw e

      case Outcome.Result(b, evts) =>
        indigoGame(b).start(b.gameConfig, Future(None), b.assets, Future(Set()), evts)
  }

}
