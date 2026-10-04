# Every external-only phone run

Times are milliseconds; bulk is MiB/s. Seek max is the maximum of 48 converted seeks; p99 equals max for that per-run sample size.

| Run | Native seek median / max | Bulk | WAV seconds | HEAD median / p99 | Header range median / p99 | Converted seek median / max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| lazy-flac-after-0 | 9.510 / 23.964 | 39.747 | 0.808970 | 12.547 / 22.400 | 13.623 / 25.126 | 12.663 / 24.386 |
| lazy-flac-before-1 | 13.729 / 20.569 | 40.012 | 0.856464 | 12.042 / 21.130 | 11.784 / 21.570 | 13.173 / 19.969 |
| lazy-flac-before-2 | 16.150 / 30.465 | 39.465 | 0.877573 | 12.781 / 24.634 | 13.252 / 25.809 | 12.170 / 20.661 |
| lazy-flac-after-3 | 16.148 / 19.017 | 39.562 | 0.888783 | 19.167 / 25.284 | 19.892 / 24.885 | 15.839 / 26.445 |
| lazy-flac-before-4 | 15.040 / 20.985 | 39.393 | 0.890966 | 19.641 / 25.769 | 20.152 / 26.178 | 12.502 / 23.276 |
| lazy-flac-after-5 | 16.126 / 20.829 | 39.340 | 0.894666 | 19.442 / 24.351 | 19.358 / 25.463 | 14.169 / 22.892 |
| lazy-flac-confirmation-before-0 | 10.071 / 15.801 | 39.673 | 0.856273 | 13.398 / 25.321 | 12.494 / 25.965 | 13.675 / 23.013 |
| lazy-flac-confirmation-after-1 | 10.014 / 17.952 | 40.684 | 0.834118 | 14.177 / 28.250 | 15.067 / 28.062 | 12.611 / 23.863 |
| lazy-flac-confirmation-after-2 | 16.210 / 26.467 | 39.165 | 0.902863 | 13.834 / 26.381 | 15.403 / 25.632 | 13.086 / 25.064 |
| lazy-flac-confirmation-before-3 | 14.552 / 25.410 | 39.147 | 0.886122 | 15.150 / 28.242 | 14.551 / 28.161 | 14.622 / 27.075 |
| lazy-flac-confirmation-after-4 | 16.398 / 24.466 | 38.948 | 0.905313 | 19.837 / 26.232 | 19.910 / 26.270 | 9.137 / 24.943 |
| lazy-flac-confirmation-before-5 | 11.385 / 15.266 | 40.691 | 0.845566 | 19.832 / 26.046 | 19.335 / 24.819 | 15.583 / 23.682 |

These are per-run sampled maxima, not true process high-water marks.

| Run | Maximum sampled PSS MiB | Maximum sampled Java heap PSS MiB | Battery temperature range °C | Visible metadata GC records |
| --- | ---: | ---: | ---: | ---: |
| lazy-flac-after-0 | 270.885 | 68.031 | 30.1–31.7 | 0 |
| lazy-flac-before-1 | 225.769 | 33.812 | 33.5–34.5 | 0 |
| lazy-flac-before-2 | 225.474 | 29.434 | 35.5–36.4 | 0 |
| lazy-flac-after-3 | 255.260 | 56.676 | 36.1–36.7 | 0 |
| lazy-flac-before-4 | 220.283 | 31.965 | 35.9–35.9 | 0 |
| lazy-flac-after-5 | 246.169 | 53.172 | 35.5–35.6 | 0 |
| lazy-flac-confirmation-before-0 | 227.373 | 34.105 | 33.8–35.3 | 0 |
| lazy-flac-confirmation-after-1 | 259.438 | 60.363 | 36.7–37.3 | 0 |
| lazy-flac-confirmation-after-2 | 266.615 | 70.703 | 37.6–37.7 | 0 |
| lazy-flac-confirmation-before-3 | 227.671 | 31.441 | 37.8–38.1 | 0 |
| lazy-flac-confirmation-after-4 | 245.291 | 51.879 | 37.8–38.2 | 0 |
| lazy-flac-confirmation-before-5 | 211.601 | 26.574 | 37.7–38.0 | 0 |
