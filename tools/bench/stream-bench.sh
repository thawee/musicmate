#!/usr/bin/env bash
# Benchmarks MusicMate's streaming engine from a computer on the same LAN.
#
#   tools/bench/stream-bench.sh <phone-ip> <track-id> [label]
#
# Run it once per engine (Music Center > Server > engine), using the same large track
# (ideally DSD or 24/192 FLAC), and compare the summary lines. Measures:
#   - single-stream throughput (whole file)
#   - seek latency: time to first byte for random 256 KB ranges
#   - parallel throughput: 4 simultaneous whole-file downloads
#   - app CPU while streaming, if adb is connected (optional)
set -euo pipefail

IP=${1:?usage: stream-bench.sh <phone-ip> <track-id> [label]}
TRACK=${2:?track id required (see /music/<id>/ URLs in the WebUI)}
LABEL=${3:-engine}
PORT=9000
URL="http://$IP:$PORT/music/$TRACK/file"
PKG=apincer.android.mmate
SEEKS=20
PARALLEL=4

size=$(curl -sfI "$URL" | tr -d '\r' | awk -F': ' 'tolower($1)=="content-length"{print $2}')
[ -n "$size" ] || { echo "No Content-Length from $URL (is the server running?)" >&2; exit 1; }
echo "[$LABEL] $URL  size=$((size / 1048576)) MB"

cpu_sampler() {
    command -v adb >/dev/null && adb get-state >/dev/null 2>&1 || return 0
    local pid; pid=$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')
    [ -n "$pid" ] || return 0
    while :; do adb shell top -b -n 1 -p "$pid" 2>/dev/null | awk -v p="$pid" '$1==p{print $9}'; sleep 1; done
}

# 1. Single stream
cpu_sampler > /tmp/stream-bench-cpu.$$ & sampler=$!
single=$(curl -s -o /dev/null -w '%{speed_download}' "$URL")
kill $sampler 2>/dev/null || true

# 2. Seek latency (time to first byte for random ranges)
ttfbs=()
for _ in $(seq $SEEKS); do
    start=$(( (RANDOM * 32768 + RANDOM) % (size - 262144) ))
    t=$(curl -s -o /dev/null -r "$start-$((start + 262143))" -w '%{time_starttransfer}' "$URL")
    ttfbs+=("$t")
done
sorted=$(printf '%s\n' "${ttfbs[@]}" | sort -n)
p50=$(echo "$sorted" | awk -v n=$SEEKS 'NR==int(n*0.5)+1')
p95=$(echo "$sorted" | awk -v n=$SEEKS 'NR==int(n*0.95)+1')

# 3. Parallel streams
now() { perl -MTime::HiRes=time -e 'printf "%.3f", time'; }  # portable sub-second clock (BSD date lacks %N)
pstart=$(now)
for _ in $(seq $PARALLEL); do curl -s -o /dev/null "$URL" & done
wait
pend=$(now)
parallel=$(awk -v s="$size" -v n=$PARALLEL -v a="$pstart" -v b="$pend" 'BEGIN{printf "%.1f", s*n/(b-a)/1048576}')

cpu=$(awk '{s+=$1; n++} END{if(n) printf "%.0f%%", s/n; else print "n/a"}' /tmp/stream-bench-cpu.$$ 2>/dev/null || echo n/a)
rm -f /tmp/stream-bench-cpu.$$

awk -v l="$LABEL" -v s="$single" -v p50="$p50" -v p95="$p95" -v par="$parallel" -v n=$PARALLEL -v cpu="$cpu" 'BEGIN{
    printf "[%s] single=%.1f MB/s  seekTTFB p50=%.0f ms p95=%.0f ms  parallel(%d)=%s MB/s  appCPU=%s\n",
           l, s/1048576, p50*1000, p95*1000, n, par, cpu }'
