package com.forge.hypertrophy.domain.usecase

import java.time.Clock
import java.time.LocalDate

/** Calendar date of this clock in its zone. */
internal fun Clock.localDate(): LocalDate = instant().atZone(zone).toLocalDate()
