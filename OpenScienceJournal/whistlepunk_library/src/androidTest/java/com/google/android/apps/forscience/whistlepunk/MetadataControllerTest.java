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

package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetadataControllerTest extends AndroidTestCase {
    public void testReorderExperiments() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        Experiment e1 = mmm.newExperiment(1, "e1");
        Experiment e2 = mmm.newExperiment(2, "e2");
        DataController dc = buildDataController(mmm);
        final ExplodingFactory explodingFactory = new ExplodingFactory();
        MetadataController mc = new MetadataController(dc, explodingFactory);
        RecordingMetadataListener listener = new RecordingMetadataListener();
        String e1id = e1.getExperimentId();
        String e2id = e2.getExperimentId();

        String listenerKey = "key";

        listener.expectedExperimentId = e2id;
        mc.addExperimentChangeListener(listenerKey, listener);
        listener.assertListenerCalled(1);

        // e1 is now first in the list
        listener.expectedExperimentId = e1id;
        mc.changeSelectedExperiment(e1);
        listener.assertListenerCalled(1);

        mc.removeExperimentChangeListener(listenerKey);

        listener.expectedExperimentId = e1id;
        mc.addExperimentChangeListener(listenerKey, listener);
        listener.assertListenerCalled(1);
    }

    public void testGetNameAfterSettingListener() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        Experiment e1 = mmm.newExperiment(1, "e1");
        e1.setTitle("E1 title");
        Experiment e2 = mmm.newExperiment(2, "e2");
        e2.setTitle("E2 title");
        DataController dc = buildDataController(mmm);
        final ExplodingFactory explodingFactory = new ExplodingFactory();
        MetadataController mc = new MetadataController(dc, explodingFactory);
        RecordingMetadataListener listener = new RecordingMetadataListener();
        String e1id = e1.getExperimentId();
        String e2id = e2.getExperimentId();

        listener.expectedExperimentId = e2id;
        mc.addExperimentChangeListener("listenerKey", listener);
        assertEquals(e2.getTitle(), mc.getExperimentName(getContext()));
    }

    @NonNull
    private DataController buildDataController(MemoryMetadataManager mmm) {
        return new InMemorySensorDatabase().makeSimpleController(mmm);
    }

    private static class RecordingMetadataListener implements MetadataController
            .MetadataChangeListener {
        public String expectedExperimentId;
        private int mListenerCalls = 0;

        @Override
        public void onMetadataChanged(Experiment selectedExperiment) {
            String id = selectedExperiment.getExperimentId();
            if (expectedExperimentId != null) {
                assertEquals(expectedExperimentId, id);
            }
            mListenerCalls++;
        }

        public void assertListenerCalled(int times) {
            assertEquals(times, mListenerCalls);
            mListenerCalls = 0;
        }
    }

}