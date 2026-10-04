"""Recalculate initial/confirmation/combined comparisons from archived raw phone runs."""
import json
import math
import pathlib
import statistics

root = pathlib.Path(__file__).resolve().parent
series = {name: json.loads((root / name / "phone.json").read_text())
          for name in ("initial", "confirmation")}
series["combined"] = series["initial"] + series["confirmation"]
metrics = {
    "native_seek_median_ms": lambda r: r["seek_p50_ms"],
    "native_seek_max_ms": lambda r: r["seek_p95_ms"],
    "bulk_MiB_s": lambda r: r["parallel_MB_s"],
    "native_single_s": lambda r: r["single"]["seconds"],
    "native_TTFB_ms": lambda r: r["single"]["ttfb_ms"],
    "WAV_s": lambda r: r["converted"]["seconds"],
    "WAV_TTFB_ms": lambda r: r["converted"]["ttfb_ms"],
    "HEAD_median_ms": lambda r: r["converted_head"]["median_ms"],
    "HEAD_p99_ms": lambda r: r["converted_head"]["p99_ms"],
    "header_median_ms": lambda r: r["converted_header_range"]["median_ms"],
    "header_p99_ms": lambda r: r["converted_header_range"]["p99_ms"],
    "converted_seek_median_ms": lambda r: r["converted_seek"]["median_ms"],
    "converted_seek_max_ms": lambda r: r["converted_seek"]["max_ms"],
    "sampled_PSS_max_MiB": lambda r: max(v["pss_kib"] for v in r["memory"].values()) / 1024,
    "sampled_Java_heap_PSS_max_MiB": lambda r: max(v["java_heap_pss_kib"] for v in r["memory"].values()) / 1024,
}
summary = {}
for name, data in series.items():
    output = {}
    for metric, value in metrics.items():
        before = statistics.median(value(r) for r in data if "-before-" in r["label"])
        after = statistics.median(value(r) for r in data if "-after-" in r["label"])
        output[metric] = {"before": before, "after": after, "change_pct": 100 * (after / before - 1)}
    for label in ("before", "after"):
        rows = [r for r in data if f"-{label}-" in r["label"]]
        timings = sorted(v["ttfb_ms"] for r in rows for v in r["converted_range_samples"])
        output[label + "_pooled_converted_seek"] = {
            "n": len(timings), "p95_ms": timings[math.ceil(len(timings) * .95) - 1],
            "p99_ms": timings[math.ceil(len(timings) * .99) - 1], "max_ms": timings[-1]}
        temperatures = [v["battery_tenths_c"] / 10 for r in rows for v in r["memory"].values()]
        output[label + "_conditions"] = {
            "min_battery_C": min(temperatures), "max_battery_C": max(temperatures),
            "visible_metadata_gc": sum(r["metadata_gc"]["visible_gc_records"] for r in rows)}
    summary[name] = output
expected = json.loads((root / "initial/phone-summary.json").read_text())
if summary != expected:
    raise AssertionError("Calculated comparisons differ from archived summary")
print("Verified every calculated initial/confirmation/combined comparison against raw runs")
