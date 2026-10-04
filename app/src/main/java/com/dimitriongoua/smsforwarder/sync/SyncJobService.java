package com.dimitriongoua.smsforwarder.sync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.dimitriongoua.smsforwarder.send.Forwarder;

/** Exécute la synchronisation planifiée par {@link SyncScheduler}, hors du fil principal. */
public class SyncJobService extends JobService {
    private static final String TAG = SyncJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters params) {
        Forwarder.EXECUTOR.execute(() -> {
            boolean retry = false;
            try {
                SyncEngine.Result result = SyncEngine.with(this).run();
                if (result.open > 0) {
                    // La tâche de reprise est relancée avec un délai croissant ; la tâche
                    // périodique, elle, la planifie.
                    if (SyncScheduler.isRetryJob(params.getJobId())) retry = true;
                    else SyncScheduler.scheduleRetry(this);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Synchronisation impossible", e);
                retry = true;
            } finally {
                jobFinished(params, retry);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Réseau perdu ou tâche interrompue : elle sera relancée.
        return true;
    }
}
