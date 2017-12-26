package me.andresp.statemachine

import com.natpryce.konfig.Configuration
import me.andresp.config.config
import me.andresp.statemachine.StateId.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule

class StateMachine(private val node: Node, private val states: Map<StateId, State>, initialState: State) {
    var currentState = initialState
        private set

    private val timer = Timer(true)
    private var timerTask: TimerTask? = null

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StateMachine::class.java)
        val ELECTION_TIMEOUT_RANGE_MS = 150..300

        fun construct(cfg: Configuration): StateMachine {
            val node = Node(cfg[config.numberNodes])
            val states = mapOf(
                    FOLLOWER to FollowerState(node),
                    CANDIDATE to CandidateState(node),
                    LEADER to LeaderState(node)
            )
            return StateMachine(node, states, states[FOLLOWER]!!)
        }
    }

    fun start() {
        scheduleTimeout()
    }

    private fun scheduleTimeout() {
        timerTask?.cancel()
        timerTask = timer.schedule(ELECTION_TIMEOUT_RANGE_MS.random().toLong(), {
            logger.info("Timeout reached. Injecting timeout event.")
            handle(LeaderHeartbeatTimeout(node.currentElectionTerm))
        })
    }

    fun handle(e: Event) {
        val newStateId = currentState.handle(e)
        val newState = states[newStateId]!!
        if (newState != currentState) {
            logger.info("STATE CHANGE: ${currentState.id} -> ${newState.id}")
            currentState = newState
            currentState.enter()
        }
    }
}

fun ClosedRange<Int>.random() = Random().nextInt(endInclusive + 1 - start) + start
