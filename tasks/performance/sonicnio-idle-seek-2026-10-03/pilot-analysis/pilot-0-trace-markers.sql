SELECT p.pid,t.tid,s.ts,s.dur,s.name FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid) JOIN process p USING(upid) WHERE s.name GLOB 'client-*' ORDER BY s.ts;
