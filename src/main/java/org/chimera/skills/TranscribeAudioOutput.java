package org.chimera.skills;

import java.util.List;

public record TranscribeAudioOutput(
    String transcript, List<TranscriptSegment> segments, double confidence) {}
