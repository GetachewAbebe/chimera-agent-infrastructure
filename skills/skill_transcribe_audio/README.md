# skill_transcribe_audio

## Purpose
Transcribe speech from downloaded media into timestamped text for content generation.

## Input Contract

```json
{
  "audio_path": "string",
  "language": "en",
  "diarization": false
}
```

## Output Contract

```json
{
  "transcript": "string",
  "segments": [
    {
      "start_ms": 0,
      "end_ms": 1200,
      "text": "hello world"
    }
  ],
  "confidence": 0.92
}
```

## Error Contract

- `AudioNotFoundError`
- `UnsupportedFormatError`
- `TranscriptionTimeoutError`
- `BudgetExceededException`

## Security

- Mask PII in transcript output when policy requires.
- Enforce maximum input size for containment.
