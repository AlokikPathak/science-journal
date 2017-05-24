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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConnectableSensor {
    private ExternalSensorSpec mSpec;

    private String mConnectedSensorId;
    private boolean mIncluded;

    /**
     * Manages creating representations of connected and disconnected sensors from stored
     * configurations.
     */
    public static class Connector {
        /**
         * Create an entry for an external sensor we've connected to in the past
         */
        public ConnectableSensor connected(ExternalSensorSpec spec, String connectedSensorId) {
            return new ConnectableSensor(spec, connectedSensorId, connectedSensorId != null);
        }

        /**
         * Create an entry for an external sensor we've never connected to
         */
        public ConnectableSensor disconnected(ExternalSensorSpec spec) {
            return new ConnectableSensor(spec, null, false);
        }

        /**
         * Create an entry for an internal built-in sensor that we know how to retrieve from {@link
         * com.google.android.apps.forscience.whistlepunk.SensorRegistry}
         */
        public ConnectableSensor builtIn(String sensorId, boolean included) {
            return new ConnectableSensor(null, sensorId, included);
        }

        /**
         * @return a new ConnectableSensor that's like this one, but in a disconnected state.
         */
        public ConnectableSensor asDisconnected(ConnectableSensor sensor) {
            if (sensor.isBuiltIn()) {
                return builtIn(sensor.mConnectedSensorId, false);
            } else {
                return disconnected(sensor.mSpec);
            }
        }
    }

    /**
     * @param paired   non-null if we've already paired with this sensor, and so there's already a
     *                 sensorId in the database for this sensor.  Otherwise, it's null; we could
     *                 connect, but a sensorId would need to be created if we did
     * @param spec     specification of the sensor if external, null if built-in (see
     *                 {@link #builtIn(String, boolean)}).
     * @param included true if the sensor is included in the current experiment
     */
    private ConnectableSensor(ExternalSensorSpec spec, String connectedSensorId, boolean included) {
        mSpec = spec;
        mConnectedSensorId = connectedSensorId;
        mIncluded = included;
    }

    public static Map<String, ExternalSensorSpec> makeMap(List<ConnectableSensor> sensors) {
        Map<String, ExternalSensorSpec> map = new HashMap<>();
        for (ConnectableSensor sensor : sensors) {
            map.put(sensor.getConnectedSensorId(), sensor.getSpec());
        }
        return map;
    }

    public static Map<String, ExternalSensorSpec> makeMap(ExperimentSensors sensors) {
        return makeMap(sensors.getIncludedSensors());
    }

    public boolean isPaired() {
        return mIncluded;
    }

    public void setPaired(boolean paired) {
        mIncluded = paired;
    }

    public ExternalSensorSpec getSpec() {
        return mSpec;
    }

    /**
     * @return the appearance of this connectable sensor.  If it is an external sensor discovered
     * via the API or remembered in the database, will directly retrieve the stored appearance,
     * otherwise, use {@link SensorAppearanceProvider} to look up the built-in sensor.
     */
    public SensorAppearance getAppearance(SensorAppearanceProvider sap) {
        if (mSpec != null) {
            return mSpec.getSensorAppearance();
        } else {
            return sap.getAppearance(mConnectedSensorId);
        }
    }

    public String getAddress() {
        return mSpec.getAddress();
    }

    public String getConnectedSensorId() {
        return mConnectedSensorId;
    }

    @Override
    public String toString() {
        return "ConnectableSensor{" +
                "mSpec=" + mSpec +
                ", mConnectedSensorId='" + mConnectedSensorId + '\'' +
                '}';
    }

    public boolean shouldShowOptionsOnConnect() {
        return mSpec != null && mSpec.shouldShowOptionsOnConnect();
    }

    public String getDeviceAddress() {
        return mSpec.getDeviceAddress();
    }

    // auto-generated by Android Studio
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConnectableSensor that = (ConnectableSensor) o;

        if (!mSpec.equals(that.mSpec)) return false;
        if (mConnectedSensorId != null ? !mConnectedSensorId.equals(that.mConnectedSensorId)
                : that.mConnectedSensorId != null) {
            return false;
        }

        return true;
    }

    // auto-generated by Android Studio
    @Override
    public int hashCode() {
        int result = mSpec.hashCode();
        result = 31 * result + (mConnectedSensorId != null ? mConnectedSensorId.hashCode() : 0);
        return result;
    }

    public boolean isSameSensor(ConnectableSensor other) {
        if (mSpec == null) {
            return Objects.equals(other.mConnectedSensorId, mConnectedSensorId);
        }
        return mSpec.isSameSensor(other.mSpec);
    }

    public boolean isUnchanged(ConnectableSensor other) {
        if (mSpec == null) {
            return Objects.equals(other.mConnectedSensorId, mConnectedSensorId);
        }
        return mSpec.isSameSensorAndSpec(other.mSpec);
    }

    public boolean isBuiltIn() {
        return mSpec == null;
    }
}
