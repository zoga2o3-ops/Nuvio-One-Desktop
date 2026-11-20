package com.nuvio.app.features.trailer

/**
 * Resolves a YouTube trailer through OneTube's engine, which streams fast through the local proxy on desktop.
 * Returns null where that isn't available, so the regular trailer lookup is used instead.
 */
internal expect suspend fun resolveOneTubeTrailer(youtubeUrl: String): TrailerPlaybackSource?
