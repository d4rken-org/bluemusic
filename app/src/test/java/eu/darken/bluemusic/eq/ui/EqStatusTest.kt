package eu.darken.bluemusic.eq.ui

import eu.darken.bluemusic.eq.core.EqSession
import eu.darken.bluemusic.eq.core.EqSessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class EqStatusTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private fun session(
        sessionId: Int,
        packageName: String? = "com.spotify.music",
        attached: Boolean = true,
        hasControl: Boolean? = true,
        openedAt: Instant = Instant.EPOCH,
        generation: Long = 1L,
    ) = EqSession(
        sessionId = sessionId,
        generation = generation,
        openedAt = openedAt,
        packageName = packageName,
        attached = attached,
        hasControl = hasControl,
    )

    private fun sessionState(vararg sessions: EqSession, generation: Long = 1L) = EqSessionState(
        listening = true,
        generation = generation,
        sessions = sessions.associateBy { it.sessionId },
    )

    private fun derive(
        sessionState: EqSessionState = sessionState(),
        targetAddress: String? = address,
        eqEnabled: Boolean = true,
        hasCapabilities: Boolean = true,
    ) = deriveEqStatus(
        deviceAddress = address,
        eqEnabled = eqEnabled,
        hasCapabilities = hasCapabilities,
        targetAddress = targetAddress,
        sessionState = sessionState,
    )

    @Test
    fun `a disabled equalizer has no status`() {
        derive(eqEnabled = false, sessionState = sessionState(session(11))) shouldBe null
    }

    @Test
    fun `an engine we cannot use has no status`() {
        derive(hasCapabilities = false, sessionState = sessionState(session(11))) shouldBe null
    }

    @Test
    fun `another device holding the equalizer is inactive for us`() {
        derive(targetAddress = "11:22:33:44:55:66", sessionState = sessionState(session(11))) shouldBe
                EqStatus.InactiveForDevice
    }

    @Test
    fun `no target at all is inactive for us`() {
        derive(targetAddress = null, sessionState = sessionState(session(11))) shouldBe EqStatus.InactiveForDevice
    }

    @Test
    fun `our device without sessions is waiting`() {
        derive(sessionState = sessionState()) shouldBe EqStatus.Waiting
    }

    @Test
    fun `sessions of an older generation don't count`() {
        derive(sessionState = sessionState(session(11, generation = 1L), generation = 2L)) shouldBe EqStatus.Waiting
    }

    @Test
    fun `an attached session we control is active`() {
        derive(sessionState = sessionState(session(11))) shouldBe
                EqStatus.Active(EqStatusApp("com.spotify.music"), multiple = false)
    }

    @Test
    fun `a session we are not attached to has no control`() {
        derive(sessionState = sessionState(session(11, attached = false))) shouldBe
                EqStatus.NoControl(EqStatusApp("com.spotify.music"))
    }

    @Test
    fun `an attached session whose engine we don't control has no control`() {
        derive(sessionState = sessionState(session(11, hasControl = false))) shouldBe
                EqStatus.NoControl(EqStatusApp("com.spotify.music"))
    }

    @Test
    fun `an unanswered control state has no control`() {
        derive(sessionState = sessionState(session(11, hasControl = null))) shouldBe
                EqStatus.NoControl(EqStatusApp("com.spotify.music"))
    }

    @Test
    fun `the most recent session names the app that took no control`() {
        val state = sessionState(
            session(11, packageName = "com.old.player", hasControl = false, openedAt = Instant.ofEpochMilli(1_000)),
            session(12, packageName = "com.new.player", hasControl = false, openedAt = Instant.ofEpochMilli(5_000)),
        )
        derive(sessionState = state) shouldBe EqStatus.NoControl(EqStatusApp("com.new.player"))
    }

    @Test
    fun `several sessions of the same app are still a single app`() {
        val state = sessionState(
            session(11, openedAt = Instant.ofEpochMilli(1_000)),
            session(12, openedAt = Instant.ofEpochMilli(5_000)),
        )
        derive(sessionState = state) shouldBe EqStatus.Active(EqStatusApp("com.spotify.music"), multiple = false)
    }

    @Test
    fun `several apps in control are reported as multiple`() {
        val state = sessionState(
            session(11, packageName = "com.spotify.music", openedAt = Instant.ofEpochMilli(1_000)),
            session(12, packageName = "com.google.android.apps.youtube.music", openedAt = Instant.ofEpochMilli(5_000)),
        )
        derive(sessionState = state) shouldBe EqStatus.Active(
            app = EqStatusApp("com.google.android.apps.youtube.music"),
            multiple = true,
        )
    }

    @Test
    fun `a session without a package name gets no app`() {
        derive(sessionState = sessionState(session(11, packageName = null))) shouldBe
                EqStatus.Active(app = null, multiple = false)
    }

    @Test
    fun `a named session wins over a newer nameless one`() {
        val state = sessionState(
            session(11, packageName = "com.spotify.music", openedAt = Instant.ofEpochMilli(1_000)),
            session(12, packageName = null, openedAt = Instant.ofEpochMilli(5_000)),
        )
        derive(sessionState = state) shouldBe EqStatus.Active(EqStatusApp("com.spotify.music"), multiple = false)
    }

    @Test
    fun `a controlled session outranks one without control`() {
        val state = sessionState(
            session(11, packageName = "com.silent.app", hasControl = false, openedAt = Instant.ofEpochMilli(5_000)),
            session(12, packageName = "com.spotify.music", openedAt = Instant.ofEpochMilli(1_000)),
        )
        derive(sessionState = state) shouldBe EqStatus.Active(EqStatusApp("com.spotify.music"), multiple = false)
    }
}
