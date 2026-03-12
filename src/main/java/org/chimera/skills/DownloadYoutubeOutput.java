package org.chimera.skills;

public record DownloadYoutubeOutput(
    String videoId, String localPath, int durationSeconds, String checksumSha256) {}
