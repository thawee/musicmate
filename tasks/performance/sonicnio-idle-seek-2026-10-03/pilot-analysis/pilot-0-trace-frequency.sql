SELECT c.ts,c.value,t.cpu FROM counter c JOIN cpu_counter_track t ON c.track_id=t.id WHERE t.name='cpufreq' ORDER BY c.ts;
