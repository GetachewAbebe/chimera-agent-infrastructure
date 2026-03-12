# skill_download_youtube

## Purpose
Download a source video and normalize metadata for downstream processing.

## Input Contract

```json
{
  "url": "https://youtube.com/watch?v=...",
  "max_duration_seconds": 900,
  "preferred_format": "mp4"
}
```

## Output Contract

```json
{
  "video_id": "string",
  "local_path": "string",
  "duration_seconds": 123,
  "checksum_sha256": "string"
}
```

## Error Contract

- `InvalidUrlError`
- `DownloadFailedError`
- `BudgetExceededException`

## Security

- Block private/unlisted URLs unless explicitly allowed by policy.
- Validate MIME type and file size before storage.
