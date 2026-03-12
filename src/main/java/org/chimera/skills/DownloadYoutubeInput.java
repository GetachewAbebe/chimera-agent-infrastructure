package org.chimera.skills;

public record DownloadYoutubeInput(String url, int maxDurationSeconds, String preferredFormat) {}
