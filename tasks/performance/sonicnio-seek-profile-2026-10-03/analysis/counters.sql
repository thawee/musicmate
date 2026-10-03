SELECT c.ts,c.value,t.name FROM counter c JOIN process_counter_track t ON c.track_id=t.id JOIN process p USING(upid) WHERE p.name='apincer.android.mmate' ORDER BY c.ts;
