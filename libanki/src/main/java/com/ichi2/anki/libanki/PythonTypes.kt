/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.libanki

/*
 * We can't use private typealiases until
 * https://youtrack.jetbrains.com/issue/KT-24700 is fixed
 */

typealias DeckId = Long
typealias CardId = Long
typealias DeckConfigId = Long
typealias NoteId = Long
typealias NoteTypeId = Long

/**
 * The number of non-leap seconds which have elapsed since the
 * [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) (00:00:00 UTC on 1 January 1970)
 *
 * See: [https://www.epochconverter.com/](https://www.epochconverter.com/)
 *
 * example: 6 February 2024 19:15:49 -> `1707246949`
 */
typealias EpochSeconds = Long
