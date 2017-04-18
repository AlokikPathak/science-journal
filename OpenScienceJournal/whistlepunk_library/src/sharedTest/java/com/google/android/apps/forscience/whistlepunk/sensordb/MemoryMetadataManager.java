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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.SimpleMetaDataManager;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MemoryMetadataManager implements MetaDataManager {
    private List<Experiment> mExperiments = new ArrayList<>();
    private Multimap<String, String> mExperimentIncluded = HashMultimap.create();
    private Multimap<String, String> mExperimentExcluded = HashMultimap.create();
    private ListMultimap<String, Label> mLabels = LinkedListMultimap.create();
    private ListMultimap<String, Label> mTrialLabels = LinkedListMultimap.create();
    private ListMultimap<String, ApplicationLabel> mApplicationLabels = LinkedListMultimap.create();
    private Table<String, String, TrialStats> mStats = HashBasedTable.create();
    private Map<String, List<GoosciSensorLayout.SensorLayout>> mLayouts = new HashMap<>();
    private Map<String, ExternalSensorSpec> mExternalSensors = new HashMap<>();
    private Map<String, Trial> mTrials = new HashMap<>();
    private ListMultimap<String, String> mExperimentIdsToRunIds = LinkedListMultimap.create();
    private Map<String, List<SensorTrigger>> mSensorTriggers = new HashMap<>();

    @Override
    public Experiment getExperimentById(String experimentId) {
        return null;
    }

    @Override
    public Experiment newExperiment() {
        long timestamp = System.currentTimeMillis();
        String experimentId = String.valueOf(timestamp);
        return newExperiment(timestamp, experimentId);
    }

    @NonNull
    public Experiment newExperiment(long timestamp, String experimentId) {
        Experiment experiment = new Experiment(timestamp);
        experiment.setExperimentId(experimentId);
        mExperiments.add(0, experiment);
        return experiment;
    }

    @Override
    public void deleteExperiment(Experiment experiment) {
        // TODO: test directly
        mExperiments.remove(experiment);
    }

    @Override
    public void updateExperiment(Experiment experiment) {

    }

    @Override
    public List<Experiment> getExperiments(boolean includeArchived) {
        return mExperiments;
    }

    @Override
    public void addLabel(String experimentId, String trialId, Label label) {
        if (TextUtils.equals(trialId, RecorderController.NOT_RECORDING_RUN_ID)) {
            mTrialLabels.put(trialId, label);
        } else {
            mLabels.put(experimentId, label);
        }
    }

    @Override
    public void addApplicationLabel(String experimentId, ApplicationLabel label) {
        mApplicationLabels.put(experimentId, label);
    }

    @Override
    public List<Label> getLabelsForExperiment(Experiment experiment) {
        return mLabels.get(experiment.getExperimentId());
    }

    @Override
    public List<Label> getLabelsForTrial(String trialId) {
        return mTrialLabels.get(trialId);
    }

    @Override
    public List<ApplicationLabel> getApplicationLabelsWithStartId(String startLabelId) {
        final ArrayList<ApplicationLabel> labels = new ArrayList<>();
        for (ApplicationLabel label : mApplicationLabels.values()) {
            if (label.getTrialId().equals(startLabelId)) {
                labels.add(label);
            }
        }
        return labels;
    }

    @Override
    public void setStats(String startLabelId, String sensorId, TrialStats stats) {
        mStats.put(startLabelId, sensorId, stats);
    }

    @Override
    public TrialStats getStats(String startLabelId, String sensorId) {
        return mStats.get(startLabelId, sensorId);
    }

    @Override
    public List<String> getExperimentRunIds(String experimentId, boolean includeArchived) {
        return mExperimentIdsToRunIds.get(experimentId);
    }

    @Override
    public void editLabel(Label updatedLabel) {

    }

    @Override
    public void editApplicationLabel(ApplicationLabel updatedLabel) {

    }

    @Override
    public void deleteLabel(Label label) {

    }

    @Override
    public Map<String, ExternalSensorSpec> getExternalSensors(
            Map<String, ExternalSensorProvider> providerMap) {
        return mExternalSensors;
    }

    @Override
    public ExternalSensorSpec getExternalSensorById(String id,
            Map<String, ExternalSensorProvider> providerMap) {
        return mExternalSensors.get(id);
    }

    @Override
    public void removeExternalSensor(String databaseTag) {
        mExternalSensors.remove(databaseTag);
    }


    @Override
    public String addOrGetExternalSensor(ExternalSensorSpec sensor,
            Map<String, ExternalSensorProvider> providerMap) {
        for (Map.Entry<String, ExternalSensorSpec> entry : mExternalSensors.entrySet()) {
            if (sensor.isSameSensorAndSpec(entry.getValue())) {
                return entry.getKey();
            }
        }
        int suffix = 0;
        while (mExternalSensors.containsKey(ExternalSensorSpec.getSensorId(sensor, suffix))) {
            suffix++;
        }
        String newId = ExternalSensorSpec.getSensorId(sensor, suffix);
        mExternalSensors.put(newId, cloneSensor(sensor, providerMap));
        return newId;
    }

    private ExternalSensorSpec cloneSensor(ExternalSensorSpec sensor,
            Map<String, ExternalSensorProvider> providerMap) {
        Preconditions.checkNotNull(sensor);
        Preconditions.checkNotNull(providerMap);
        String sensorType = sensor.getType();
        ExternalSensorProvider provider = providerMap.get(sensorType);
        if (provider == null) {
            throw new IllegalArgumentException("No provider for sensor type " + sensorType);
        }
        String sensorName = sensor.getName();
        byte[] sensorConfig = sensor.getConfig();
        return provider.buildSensorSpec(sensorName, sensorConfig);
    }

    @Override
    public void addSensorToExperiment(String databaseTag, String experimentId) {
        mExperimentExcluded.remove(experimentId, databaseTag);
        mExperimentIncluded.put(experimentId, databaseTag);
    }

    @Override
    public void removeSensorFromExperiment(String databaseTag, String experimentId) {
        mExperimentIncluded.remove(experimentId, databaseTag);
        mExperimentExcluded.put(experimentId, databaseTag);
    }

    @Override
    public ExperimentSensors getExperimentExternalSensors(String experimentId,
            Map<String, ExternalSensorProvider> providerMap) {
        // TODO: doesn't deal with exclusions
        List<ConnectableSensor> specs = new ArrayList<>();
        for (String id : mExperimentIncluded.get(experimentId)) {
            specs.add(ConnectableSensor.connected(mExternalSensors.get(id), id));
        }
        return new ExperimentSensors(specs, Sets.newHashSet(mExperimentExcluded.get(experimentId)));
    }

    @Override
    public void addMyDevice(InputDeviceSpec deviceSpec) {

    }

    @Override
    public void removeMyDevice(InputDeviceSpec deviceSpec) {

    }

    @Override
    public List<InputDeviceSpec> getMyDevices() {
        return Lists.newArrayList();
    }

    @Override
    public Experiment getLastUsedExperiment() {
        return null;
    }

    @Override
    public void updateLastUsedExperiment(Experiment experiment) {
        mExperiments.remove(experiment);
        mExperiments.add(0, experiment);
    }

    @Override
    public Trial newTrial(Experiment experiment, String runId, long startTimestamp,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.trialId = runId;
        trialProto.sensorLayouts = deepCopy(sensorLayouts);
        final Trial trial = Trial.fromTrial(trialProto);
        mTrials.put(trial.getTrialId(), trial);
        mExperimentIdsToRunIds.put(experiment.getExperimentId(), trial.getTrialId());
        return trial;
    }

    @Override
    public void updateTrialLayouts(String trialId, List<GoosciSensorLayout.SensorLayout> layouts) {
        List<GoosciSensorLayout.SensorLayout> sensorLayouts = mTrials.get(trialId).getSensorLayouts();
        sensorLayouts.clear();
        sensorLayouts.addAll(Arrays.asList(deepCopy(layouts)));
        mTrials.get(trialId).setSensorLayouts(sensorLayouts);
    }

    private GoosciSensorLayout.SensorLayout[] deepCopy(
            List<GoosciSensorLayout.SensorLayout> original) {
        GoosciSensorLayout.SensorLayout[] copy = new GoosciSensorLayout.SensorLayout[
                original.size()];
        for (int i = 0; i < original.size(); i++) {
            GoosciSensorLayout.SensorLayout layout = original.get(i);
            try {
                byte[] bytes = ExternalSensorSpec.getBytes(layout);
                copy[i] = (GoosciSensorLayout.SensorLayout.parseFrom(bytes));
            } catch (InvalidProtocolBufferNanoException e) {
                throw new RuntimeException(e);
            }
        }
        return copy;
    }

    @Override
    public Trial getTrial(String trialId, List<ApplicationLabel> applicationLabels,
            List<Label> labels) {
        Trial trial = mTrials.get(trialId);
        SimpleMetaDataManager.populateTrialProtoFromLabels(trial.getTrialProto(),
                applicationLabels, labels);
        return trial;
    }

    @Override
    public void setExperimentSensorLayouts(String experimentId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        mLayouts.put(experimentId, sensorLayouts);
    }

    @Override
    public List<GoosciSensorLayout.SensorLayout> getExperimentSensorLayouts(String experimentId) {
        List<GoosciSensorLayout.SensorLayout> layouts = mLayouts.get(experimentId);
        if (layouts == null) {
            return Collections.emptyList();
        } else {
            return layouts;
        }
    }

    @Override
    public void updateSensorLayout(String experimentId, int position,
            GoosciSensorLayout.SensorLayout layout) {
        List<GoosciSensorLayout.SensorLayout> layouts = mLayouts.get(experimentId);
        if (layouts.size() > position) {
            layouts.remove(position);
            layouts.add(position, layout);
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void updateTrial(Trial trial) {

    }

    @Override
    public void deleteTrial(String runId) {

    }

    @Override
    public void addSensorTrigger(SensorTrigger trigger, String experimentId) {
        String sensorId = trigger.getSensorId();
        if (mSensorTriggers.containsKey(sensorId)) {
            mSensorTriggers.get(sensorId).add(trigger);
        } else {
            List<SensorTrigger> triggers = new ArrayList<>();
            triggers.add(trigger);
            mSensorTriggers.put(sensorId, triggers);
        }
    }

    @Override
    public void updateSensorTrigger(SensorTrigger trigger) {
        if (mSensorTriggers.containsKey(trigger.getSensorId())) {
            List<SensorTrigger> triggers = mSensorTriggers.get(trigger.getSensorId());
            int index = -1;
            for (int i = 0; i < triggers.size(); i++) {
                if (TextUtils.equals(triggers.get(i).getTriggerId(), trigger.getTriggerId())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                triggers.remove(index);
                triggers.add(index, trigger);
            }
        }
    }

    @Override
    public List<SensorTrigger> getSensorTriggers(String[] triggerIds) {
        List<SensorTrigger> result = new ArrayList<>();
        for (List<SensorTrigger> triggers : mSensorTriggers.values()) {
            for (SensorTrigger trigger : triggers) {
                for (String triggerId : triggerIds) {
                    if (TextUtils.equals(trigger.getTriggerId(), triggerId)) {
                        result.add(trigger);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<SensorTrigger> getSensorTriggersForSensor(String sensorId) {
        List<SensorTrigger> result = new ArrayList<>();
        if (mSensorTriggers.containsKey(sensorId)) {
            Collections.copy(result, mSensorTriggers.get(sensorId));
        }
        return result;
    }

    @Override
    public void deleteSensorTrigger(SensorTrigger toDelete) {
        for (List<SensorTrigger> triggers : mSensorTriggers.values()) {
            Iterator<SensorTrigger> iterator = triggers.iterator();
            while (iterator.hasNext()) {
                SensorTrigger trigger = iterator.next();
                if (TextUtils.equals(trigger.getTriggerId(), toDelete.getTriggerId())) {
                    iterator.remove();
                    return;
                }
            }
        }
    }
}
