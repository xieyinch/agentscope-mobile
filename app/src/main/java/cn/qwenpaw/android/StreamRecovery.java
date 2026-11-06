package cn.qwenpaw.android;
import io.github.xieyinch.agentscope.R;
import io.github.xieyinch.agentscope.BuildConfig;

/**
 * SSE liveness and reconnect pacing. The server sends no keepalive, so silence alone is not a failure:
 * a silent stream is only abandoned when the run is idle or the status endpoint is unreachable.
 */
final class StreamRecovery {
    static final int FAST_ATTEMPTS=5;
    static final long SLOW_DELAY_MS=30_000, CHECK_INTERVAL_MS=30_000, IDLE_MS=45_000;
    static final int STATUS_FAILURES=2;
    private int attempts, statusFailures;
    private boolean replayEnded;
    private long lastEventAt;

    void reset() {attempts=0;statusFailures=0;}
    void streamStarted(boolean reconnecting,long now) {replayEnded=!reconnecting;lastEventAt=now;statusFailures=0;}
    /** Only a live event after the replay proves the reconnect worked; a replay that drops again keeps backing off. */
    void event(String type,long now) {
        lastEventAt=now;
        if(type.equals("replay_end")){replayEnded=true;return;}
        if(replayEnded)attempts=0;
    }
    boolean slow() {return attempts>=FAST_ATTEMPTS;}
    long nextDelay() {return slow()?SLOW_DELAY_MS:Math.min(8000,1000L<<attempts++);}
    int attempts() {return attempts;}
    boolean idleCheckDue(long now) {return now-lastEventAt>=IDLE_MS;}
    void statusOk() {statusFailures=0;}
    /** @return true when the stream should be treated as dead. */
    boolean statusFailed() {return ++statusFailures>=STATUS_FAILURES;}
}
