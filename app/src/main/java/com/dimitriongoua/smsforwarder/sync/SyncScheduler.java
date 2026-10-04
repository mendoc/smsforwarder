package com.dimitriongoua.smsforwarder.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * Planification de la synchronisation par JobScheduler, toujours avec une connexion réseau :
 * <ul>
 *     <li>périodique (15 minutes), conservée après un redémarrage du téléphone ;</li>
 *     <li>ponctuelle, dès le retour du réseau, quand des envois restent à retenter ou au
 *     démarrage du téléphone.</li>
 * </ul>
 */
public final class SyncScheduler {
    private static final String TAG = SyncScheduler.class.getSimpleName();
    private static final int PERIODIC_JOB_ID = 1001;
    private static final int RETRY_JOB_ID = 1002;
    private static final long PERIOD_MS = 15 * 60 * 1000;
    private static final long RETRY_DELAY_MS = 30 * 1000;

    private SyncScheduler() {
    }

    /** Tâche ponctuelle de reprise : elle se replanifie elle-même (backoff) tant qu'il reste des envois. */
    static boolean isRetryJob(int jobId) {
        return jobId == RETRY_JOB_ID;
    }

    /** Planifie la synchronisation périodique si elle ne l'est pas déjà. */
    public static void ensurePeriodic(Context context) {
        JobScheduler scheduler = scheduler(context);
        if (scheduler == null || scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(PERIODIC_JOB_ID, new ComponentName(context, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        schedule(scheduler, job);
    }

    /**
     * Synchronisation dès qu'une connexion réseau est disponible (au plus tôt dans 30 s).
     * Sans effet si elle est déjà planifiée ou en cours.
     */
    public static void scheduleRetry(Context context) {
        JobScheduler scheduler = scheduler(context);
        if (scheduler == null || scheduler.getPendingJob(RETRY_JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(RETRY_JOB_ID, new ComponentName(context, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(RETRY_DELAY_MS)
                .setBackoffCriteria(RETRY_DELAY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        schedule(scheduler, job);
    }

    private static JobScheduler scheduler(Context context) {
        return (JobScheduler) context.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private static void schedule(JobScheduler scheduler, JobInfo job) {
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Planification de la synchronisation refusée (tâche " + job.getId() + ")");
        }
    }
}
