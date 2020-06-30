package indigoextras.subsystems

import utest._
import indigo.shared.scenegraph.Graphic
import indigo.shared.events.GlobalEvent
import indigo.shared.time.GameTime
import indigo.shared.dice.Dice
import indigo.shared.datatypes.Point
import indigo.shared.Outcome
import indigo.shared.assets.AssetName
import indigo.shared.datatypes.Material
import indigo.shared.time.Seconds
import indigo.shared.events.InputState
import indigo.shared.scenegraph.SceneGraphNode
import indigo.shared.temporal.Signal
import indigo.shared.temporal.SignalFunction
import indigo.shared.scenegraph.SceneUpdateFragment

object AutomataTests extends TestSuite {

  import indigoextras.subsystems.FakeFrameContext._

  final case class MyCullEvent(message: String) extends GlobalEvent

  val eventInstance =
    MyCullEvent("Hello, I'm dead.")

  val poolKey: AutomataPoolKey =
    AutomataPoolKey("test")

  val automaton: Automaton =
    Automaton(
      Graphic(0, 0, 10, 10, 1, Material.Textured(AssetName("fish"))),
      Seconds(1)
    ).withOnCullEvent { _ =>
        List(eventInstance)
      }
      .withModifier(ModiferFunctions.signal)

  val automata: Automata =
    Automata(poolKey, automaton, Automata.Layer.Game)

  val startingState: List[SpawnedAutomaton] =
    automata
      .update(context(1), Nil)(AutomataEvent.Spawn(poolKey, Point.zero, None, None))
      .state

  val tests: Tests =
    Tests {

      "Starting state should contain 1 automaton" - {

        val expected =
          SpawnedAutomaton(
            automaton,
            new AutomatonSeedValues(
              Point.zero,
              Seconds(0),
              Seconds(1),
              1, // comes from the fake frame context
              None
            )
          )

        startingState.length ==> 1
        startingState.head ==> expected
      }

      "should move a particle with a modifier signal" - {

        import ModiferFunctions._

        // Test the signal
        val seed = new AutomatonSeedValues(Point.zero, Seconds.zero, Seconds(1), 0, None)

        makePosition(seed).at(Seconds(0)) ==> Point(0, 0)
        makePosition(seed).at(Seconds(0.5)) ==> Point(0, -15)
        makePosition(seed).at(Seconds(1)) ==> Point(0, -30)

        // Test the automaton
        def drawAt(time: Seconds): Graphic = {
          val ctx = context(1, time, time)

          val nextState =
            automata
              .update(ctx, startingState)(AutomataEvent.Update(poolKey))
              .state

          automata
            .render(ctx, nextState)
            .gameLayer
            .nodes
            .collect { case g: Graphic => g }
            .head
        }

        drawAt(Seconds(0)).position ==> Point(0, 0)
        drawAt(Seconds(0.5)).position ==> Point(0, -15)
        drawAt(Seconds(0.9)).position ==> Point(0, -27)
      }

      "culling an automaton should result in an event" - {

        // 1 ms over the lifespan, so should be culled
        val outcome =
          automata
            .update(context(1, Seconds(1)), startingState)(AutomataEvent.Update(poolKey))

        outcome.state.length ==> 0
        outcome.globalEvents.head ==> eventInstance
      }

      "KillAll should... kill all the automatons." - {

        // At any time, KillAll, should remove all automatons without trigger cull events.
        val outcome =
          automata
            .update(context(1, Seconds(0)), startingState)(AutomataEvent.KillAll(poolKey))

        outcome.state.isEmpty ==> true
        outcome.globalEvents.isEmpty ==> true
      }

    }

  object ModiferFunctions {

    val makePosition: AutomatonSeedValues => Signal[Point] =
      seed =>
        Signal { time =>
          seed.spawnedAt +
            Point(
              0,
              -(30d * seed.progression(time)).toInt
            )
        }

    val signal: (AutomatonSeedValues, SceneGraphNode) => Signal[AutomatonUpdate] =
      (seed, sceneGraphNode) =>
        makePosition(seed).map { position =>
          AutomatonUpdate(
            sceneGraphNode match {
              case g: Graphic =>
                List(g.moveTo(position))

              case _ =>
                Nil
            },
            Nil
          )
        }

  }

}
