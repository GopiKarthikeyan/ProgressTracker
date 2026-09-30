package com.forge.hypertrophy.data.transfer

enum class ImportMode {
    /** Rewrites the active program's routine and merges the library by name. */
    REPLACE_ROUTINE_MERGE_LIBRARY,

    /**
     * Rewrites the active program's routine, merges library rows that share a
     * name, hard-deletes library rows nothing references, and archives the ones
     * history or another program still points at.
     */
    REPLACE_EVERYTHING,

    /** Inserts another program. It becomes active only when none is active yet. */
    ADD_PROGRAM,
}
