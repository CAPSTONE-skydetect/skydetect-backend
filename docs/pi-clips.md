# Pi clip jobs

The Pi Compose override enables the Spring-owned clip API. Spring Security
authenticates requests and checks CSRF; MySQL owns new clip status, ownership
and media metadata.

| Route | Behavior |
| --- | --- |
| POST /api/clips | Persist PENDING and return 202 |
| GET /api/clips | List up to 100 recent jobs owned by the current user |
| GET /api/clips/{id} | Return PENDING, READY or FAILED for the owner |
| GET /api/clips/{id}/video | Serve the MP4 to its owner, including Range requests |

Spring's receive time anchors the -3/+10 second request. Browser click/media
time is diagnostic only. The internal Python worker receives an ID and timestamp,
uses finalized MediaMTX segments and existing gap/frame mapping checks, and
returns media metadata. Spring validates metadata and file size before marking
READY. Video files remain on disk. Sensor wall-clock alignment is not claimed.

The override configures `SKYDETECT_CLIPS_WORKER_URL` and the read-only
`SKYDETECT_CLIPS_DIRECTORY`. Without a worker URL, creation returns 503.
Do not publish the internal worker port or proxy its /internal routes.

The local configuration uses one Spring instance and up to two active jobs.
Startup marks interrupted PENDING rows FAILED rather than retrying extraction.
Schema creation uses the application's existing Hibernate update policy.
Multiple replicas and managed migrations are outside this local rollout.

Legacy clip_ IDs and analyses stay in SQLite. The Pi frontend reads both lists.
New spring_clip_ IDs exist only in MySQL; their ROI analysis uses the existing
browser-to-AI route and is not yet a persisted server analysis job. Old rows
and files are not migrated or deleted.
