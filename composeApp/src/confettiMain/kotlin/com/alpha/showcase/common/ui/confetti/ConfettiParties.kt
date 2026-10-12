package com.alpha.showcase.common.ui.confetti

import io.github.vinceglb.confettikit.core.Angle
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.Spread
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlin.time.Duration.Companion.seconds

internal fun confettiParties(type: ConfettiType): List<Party> {
    return when (type) {
        ConfettiType.Celebration -> listOf(
            Party(
                speed = 0f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = Angle.BOTTOM,
                spread = Spread.ROUND,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 5.seconds).perSecond(100),
                position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)),
            ),
        )

        ConfettiType.Success -> listOf(
            Party(
                speed = 0f,
                maxSpeed = 10f,
                damping = 0.92f,
                angle = Angle.BOTTOM,
                spread = Spread.ROUND,
                colors = listOf(0x8bc34a, 0x4caf50, 0xcddc39, 0xa5d6a7),
                emitter = Emitter(duration = 2.5.seconds).perSecond(70),
                position = Position.Relative(0.15, 0.0).between(Position.Relative(0.85, 0.0)),
            ),
        )

        ConfettiType.Burst -> listOf(
            Party(
                speed = 0f,
                maxSpeed = 20f,
                damping = 0.88f,
                angle = Angle.BOTTOM,
                spread = Spread.SMALL,
                colors = listOf(0xffd54f, 0xff8a65, 0x4fc3f7, 0xce93d8),
                emitter = Emitter(duration = 1.2.seconds).perSecond(180),
                position = Position.Relative(0.5, 0.15),
            ),
        )
    }
}
