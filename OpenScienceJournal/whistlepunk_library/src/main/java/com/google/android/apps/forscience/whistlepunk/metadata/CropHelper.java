/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Helper class for cropping.
 */
public class CropHelper {
    public static final String TAG = "CropHelper";

    // Time buffer for non-overlapping crop: We do not allow crop less than 1 second.
    // If this is changed, make sure to update R.string.crop_failed_range_too_small as well.
    public static final long MINIMUM_CROP_MILLIS = 1000;

    private static final int DATAPOINTS_PER_LOAD = 500;

    private static final String ACTION_CROP_STATS_RECALCULATED = "action_crop_stats_recalculated";
    public static final String EXTRA_SENSOR_ID = "extra_sensor_id";
    public static final String EXTRA_RUN_ID = "extra_run_id";

    private static final IntentFilter STATS_INTENT_FILTER = new IntentFilter(
            ACTION_CROP_STATS_RECALCULATED);

    static class CropLabels {
        ApplicationLabel cropStartLabel;
        ApplicationLabel cropEndLabel;

        CropLabels() {

        }
    }

    private static class ProcessPriorityThreadFactory implements ThreadFactory {
        private final int mThreadPriority;

        ProcessPriorityThreadFactory(int threadPriority) {
            super();
            mThreadPriority = threadPriority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setPriority(mThreadPriority);
            return thread;
        }
    }

    public interface CropRunListener {
        /**
         * Called when a crop is completed, i.e. the metadata for the experiment is updated.
         * The min, max and average may not yet be recalculated.
         */
        void onCropCompleted();

        /**
         * Called when a crop fails to complete. No changes were made to the experiment, min,
         * max or average.
         */
        void onCropFailed(int errorId);
    }

    private int mStatsUpdated = 0;
    private Executor mCropStatsExecutor;
    private DataController mDataController;

    public CropHelper(DataController dataController) {
        this(Executors.newSingleThreadExecutor(new ProcessPriorityThreadFactory(
                android.os.Process.THREAD_PRIORITY_BACKGROUND)), dataController);
    }

    @VisibleForTesting
    CropHelper(Executor executor, DataController dataController) {
        mCropStatsExecutor = executor;
        mDataController = dataController;
    }

    public void cropRun(final Context context, final ExperimentRun run, long startTimestamp,
            long endTimestamp, final CropRunListener listener) {

        // Are we trying to crop too wide? Too narrow? Are the timestamps valid?
        if (startTimestamp < run.getOriginalFirstTimestamp() ||
                run.getOriginalLastTimestamp() < endTimestamp) {
            logEvent(context, TrackerConstants.ACTION_CROP_FAILED);
            listener.onCropFailed(R.string.crop_failed_range_too_large);
            return;
        }
        if (endTimestamp - MINIMUM_CROP_MILLIS <= startTimestamp) {
            logEvent(context, TrackerConstants.ACTION_CROP_FAILED);
            listener.onCropFailed(R.string.crop_failed_range_too_small);
            return;
        }

        final CropLabels cropLabels = run.getCropLabels();
        if (cropLabels.cropStartLabel != null && cropLabels.cropEndLabel != null) {
            // It is already cropped, so we can edit the old crop labels.
            cropLabels.cropStartLabel.setTimestamp(startTimestamp);
            cropLabels.cropEndLabel.setTimestamp(endTimestamp);
            run.setCropLabels(cropLabels);
            mDataController.editApplicationLabel(cropLabels.cropStartLabel,
                    new LoggingConsumer<Success>(TAG, "edit crop start label") {
                        @Override
                        public void success(Success value) {
                            mDataController.editApplicationLabel(cropLabels.cropEndLabel,
                                    new LoggingConsumer<Success>(TAG,
                                            "edit crop end label") {
                                        @Override
                                        public void success(Success value) {
                                            markRunStatsForAdjustment(context, run, listener);
                                        }
                                    });
                        }
                    });
        } else if (cropLabels.cropStartLabel == null && cropLabels.cropEndLabel == null) {
            // Otherwise we make new crop labels.
            ApplicationLabel cropStartLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_START, mDataController.generateNewLabelId(),
                    run.getTrialId(), startTimestamp);
            final ApplicationLabel cropEndLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_END, mDataController.generateNewLabelId(),
                    run.getTrialId(), endTimestamp);
            cropStartLabel.setExperimentId(run.getExperimentId());
            cropEndLabel.setExperimentId(run.getExperimentId());

            // Update the run.
            cropLabels.cropStartLabel = cropStartLabel;
            cropLabels.cropEndLabel = cropEndLabel;
            run.setCropLabels(cropLabels);

            // Add new crop labels to the database.
            mDataController.addCropApplicationLabel(cropStartLabel,
                    new LoggingConsumer<ApplicationLabel>(TAG, "add crop start label") {
                        @Override
                        public void success(ApplicationLabel value) {
                            mDataController.addCropApplicationLabel(cropEndLabel,
                                    new LoggingConsumer<ApplicationLabel>(TAG,
                                            "Add crop end label") {
                                        @Override
                                        public void success(ApplicationLabel value) {
                                            markRunStatsForAdjustment(context, run, listener);
                                        }
                                    });
                        }
                    });
        } else {
            // One crop label is set and the other is not. This is an error!
            logEvent(context, TrackerConstants.ACTION_CROP_FAILED);
            listener.onCropFailed(R.string.crop_failed_invalid_state);
        }
    }

    private void markRunStatsForAdjustment(final Context context, final ExperimentRun run,
            final CropRunListener listener) {
        // First delete the min/max/avg stats, but leave the rest available, because they are used
        // in loading data by ZoomPresenter. At this point, we can go back to RunReview.
        final int statsToUpdate = run.getSensorLayouts().size();
        mStatsUpdated = 0;
        for (GoosciSensorLayout.SensorLayout layout : run.getSensorLayouts()) {
            final String sensorId = layout.sensorId;
            mDataController.setSensorStatsStatus(run.getTrialId(), sensorId,
                    GoosciTrial.SensorTrialStats.NEEDS_UPDATE,
                    new LoggingConsumer<Success>(TAG, "update stats") {
                        @Override
                        public void success(Success success) {
                            mStatsUpdated++;
                            if (mStatsUpdated == statsToUpdate) {
                                logEvent(context, TrackerConstants.ACTION_CROP_COMPLETED);
                                listener.onCropCompleted();
                            }
                            adjustRunStats(context, run, sensorId);
                        }
                    });
        }
    }

    private void logEvent(Context context, String event) {
        WhistlePunkApplication.getUsageTracker(context).trackEvent(TrackerConstants.CATEGORY_RUNS,
                event, "", 1);
    }

    private void adjustRunStats(final Context context, final ExperimentRun run,
            final String sensorId) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // Moves the current Thread into the background
                StatsAdjuster adjuster = new StatsAdjuster(sensorId, run, context);
                adjuster.recalculateStats(mDataController);
            }
        };
        // Update the stats in the background, without blocking anything.
        mCropStatsExecutor.execute(runnable);
    }

    // A class that recalculates and resaves the stats in a run.
    private class StatsAdjuster {
        private final String mSensorId;
        private final ExperimentRun mExperimentRun;
        private StatsAccumulator mStatsAccumulator;
        StreamConsumer mStreamConsumer;
        private Context mContext;

        StatsAdjuster(String sensorId, ExperimentRun run, Context context) {
            mStatsAccumulator = new StatsAccumulator(sensorId);
            mSensorId = sensorId;
            mExperimentRun = run;
            mStreamConsumer = new StreamConsumer() {
                @Override
                public void addData(long timestampMillis, double value) {
                    mStatsAccumulator.updateRecordingStreamStats(timestampMillis, value);
                }
            };
            mContext = context;
        }

        void recalculateStats(DataController dc) {
            TimeRange range = TimeRange.oldest(Range.closed(mExperimentRun.getFirstTimestamp(),
                    mExperimentRun.getLastTimestamp()));
            addReadingsToStats(dc, range);
        }

        private void addReadingsToStats(final DataController dc, final TimeRange range) {
            dc.getScalarReadings(mSensorId, /* tier 0 */ 0, range,
                    DATAPOINTS_PER_LOAD, new MaybeConsumer<ScalarReadingList>() {
                        @Override
                        public void success(ScalarReadingList list) {
                            list.deliver(mStreamConsumer);
                            if (list.size() == 0 || list.size() < DATAPOINTS_PER_LOAD ||
                                    mStatsAccumulator.getLatestTimestamp() >=
                                            mExperimentRun.getLastTimestamp()) {
                                if (!mStatsAccumulator.isInitialized()) {
                                    // There was no data in this region, so the stats are still
                                    // not valid.
                                    return;
                                }
                                // Done! Save back to the database.
                                // Note that we only need to save the stats we have changed, because
                                // each stat is stored separately. We do not need to update stats
                                // like zoom tiers and zoom levels.
                                TrialStats trialStats = mStatsAccumulator.makeSaveableStats();
                                trialStats.setStatStatus(GoosciTrial.SensorTrialStats.VALID);
                                dc.updateTrialStats(mExperimentRun.getTrialId(), mSensorId,
                                        trialStats,
                                        new LoggingConsumer<Success>(TAG, "update stats") {
                                            @Override
                                            public void success(Success value) {
                                                sendStatsUpdatedBroadcast(mContext, mSensorId,
                                                        mExperimentRun.getTrialId());
                                            }
                                        });
                            } else {
                                TimeRange nextRange = TimeRange.oldest(
                                        Range.openClosed(mStatsAccumulator.getLatestTimestamp(),
                                                mExperimentRun.getLastTimestamp()));
                                addReadingsToStats(dc, nextRange);
                            }
                        }

                        @Override
                        public void fail(Exception e) {
                            Log.e(TAG, "Error loading data to adjust stats after crop");
                        }
                    });
        }
    }

    // Use a Broadcast to tell RunReviewFragment or ExperimentDetailsFragment or anyone who uses
    // stats that the stats are updated for this sensor on this run.
    private static void sendStatsUpdatedBroadcast(Context context, String sensorId, String runId) {
        if (context == null) {
            return;
        }
        // Use a LocalBroadcastManager, because we do not need this broadcast outside the app.
        LocalBroadcastManager lbm = getBroadcastManager(context);
        Intent intent = new Intent();
        intent.setAction(ACTION_CROP_STATS_RECALCULATED);
        intent.putExtra(EXTRA_SENSOR_ID, sensorId);
        intent.putExtra(EXTRA_RUN_ID, runId);
        lbm.sendBroadcast(intent);
    }

    private static LocalBroadcastManager getBroadcastManager(Context context) {
        // For security, only use local broadcasts (See b/32803250)
        return LocalBroadcastManager.getInstance(context);
    }

    public static void registerStatsBroadcastReceiver(Context context, BroadcastReceiver receiver) {
        getBroadcastManager(context).registerReceiver(receiver, STATS_INTENT_FILTER);
    }

    public static void unregisterBroadcastReceiver(Context context, BroadcastReceiver receiver) {
        getBroadcastManager(context).unregisterReceiver(receiver);
    }

    public static boolean experimentIsLongEnoughForCrop(ExperimentRun experimentRun) {
        return experimentRun.getOriginalLastTimestamp() -
                experimentRun.getOriginalFirstTimestamp() > CropHelper.MINIMUM_CROP_MILLIS;
    }

    public void throwAwayDataOutsideCroppedRegion(DataController dc, ExperimentRun run) {
        // TODO
    }
}
