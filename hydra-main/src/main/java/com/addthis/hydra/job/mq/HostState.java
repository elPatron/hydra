/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.job.mq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.hydra.job.minion.Minion;

import com.google.common.base.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"messageType", "totalLive"})
public class HostState implements HostMessage {

    private static final long serialVersionUID = 7252930788607795642L;

    @FieldConfig private String host;
    @FieldConfig private int port;
    @FieldConfig private String uuid;
    @FieldConfig private String user;
    @FieldConfig private String path;
    @FieldConfig private String group;
    // Host State is determined by others zk group membership, by
    // definition it can not persist.
    @FieldConfig private boolean up;
    @FieldConfig private long time;
    @FieldConfig private long uptime;
    @FieldConfig private int availableTaskSlots;
    @FieldConfig private int maxTaskSlots;
    @FieldConfig private JobKey[] running;
    @FieldConfig private JobKey[] replicating;
    @FieldConfig private JobKey[] backingup;
    @FieldConfig private JobKey[] stopped;
    @FieldConfig private JobKey[] replicas;
    @FieldConfig private JobKey[] incompleteReplicas;
    @FieldConfig private JobKey[] queued;
    @FieldConfig private HostCapacity used;
    @FieldConfig private HostCapacity max;
    @FieldConfig private boolean dead;
    @FieldConfig private long lastUpdateTime;
    @FieldConfig private double histQueueSize;
    @FieldConfig private double histWaitTime;
    //TODO:  remove but need this in for now because de-serialization fails without it
    @FieldConfig private HashMap<String, Double> jobRuntimes = new HashMap<>();
    @FieldConfig private boolean readOnly;
    @FieldConfig private boolean diskReadOnly;
    @FieldConfig private boolean disabled;
    @FieldConfig private double meanActiveTasks;
    @FieldConfig private String minionTypes;

    // Do not encode this derived, internal, non-typesafe field
    private HashMap<String, Integer> jobTaskCountMap;

    public HostState() {
    }

    public HostState(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public TYPE getMessageType() {
        return TYPE.STATUS_HOST_INFO;
    }

    @Override
    public String getHostUuid() {
        return uuid;
    }

    public void setHostUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setUpdated() {
        lastUpdateTime = System.currentTimeMillis();
    }

    public boolean hasLive(JobKey jobKey) {
        if (stopped != null && Arrays.asList(stopped).contains(jobKey)) {
            return true;
        }
        if (queued != null && Arrays.asList(queued).contains(jobKey)) {
            return true;
        }
        if (running != null && Arrays.asList(running).contains(jobKey)) {
            return true;
        }
        if (replicating != null && Arrays.asList(replicating).contains(jobKey)) {
            return true;
        }
        if (backingup != null && Arrays.asList(backingup).contains(jobKey)) {
            return true;
        }
        return false;
    }

    public boolean hasIncompleteReplica(JobKey jobKey) {
        return (incompleteReplicas != null) && Arrays.asList(incompleteReplicas).contains(jobKey);
    }

    public List<JobKey> allJobKeys() {
        List<JobKey> rv = new ArrayList<>();
        for (JobKey[] jobKeys : Arrays.asList(stopped, queued, running, replicating, backingup, replicas)) {
            if (jobKeys != null) {
                rv.addAll(Arrays.asList(jobKeys));
            }
        }
        return rv;
    }

    public Integer addJob(String jobId) {
        if (this.jobTaskCountMap == null) {
            jobTaskCountMap = new HashMap<>();
        }
        int currentCount = 0;
        if (jobTaskCountMap.containsKey(jobId)) {
            currentCount = jobTaskCountMap.get(jobId);
        }
        return jobTaskCountMap.put(jobId, currentCount + 1);
    }

    public void generateJobTaskCountMap() {
        jobTaskCountMap = new HashMap<>();
        List<JobKey[]> activeJobsSources = Arrays.asList(stopped, queued, running, replicas);
        for (JobKey[] source : activeJobsSources) {
            if (source == null) {
                continue;
            }
            for (JobKey jobKey : source) {
                if (jobKey == null) {
                    continue;
                }
                Integer oldCount = jobTaskCountMap.get(jobKey.getJobUuid());
                int newCount;
                if (oldCount != null) {
                    newCount = 1 + oldCount;
                } else {
                    newCount = 1;
                }
                jobTaskCountMap.put(jobKey.getJobUuid(), newCount);
            }
        }
    }

    public Integer getTaskCount(String jobId) {
        if (jobTaskCountMap == null) {
            generateJobTaskCountMap();
        }
        if ((jobTaskCountMap != null) && jobTaskCountMap.containsKey(jobId)) {
            return jobTaskCountMap.get(jobId);
        } else {
            return 0;
        }
    }

    public boolean canMirrorTasks() {
        return up && !dead && !diskReadOnly && !disabled;
    }

    public boolean hasType(String type) {
        if (minionTypes == null) {
            minionTypes = Minion.defaultMinionType;
        }
        if (minionTypes.contains(",")) {
            return Arrays.asList(minionTypes.split(",")).contains(type);
        }
        return type.equals(minionTypes);
    }

    public int countTotalLive() {
        int total = 0;
        for (JobKey[] keys : Arrays.asList(stopped, running, replicating, backingup, queued)) {
            if (keys != null) {
                total += keys.length;
            }
        }
        return total;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("type", getMessageType())
                .add("uuid", getHostUuid())
                .add("last-update-time", getLastUpdateTime())
                .add("host", getHost())
                .add("port", getPort())
                .add("group", getGroup())
                .add("time", getTime())
                .add("uptime", getUptime())
                        // You probably only want to print these out when testing locally
                        // .add("running", getRunning())
                        // .add("stopped", getStopped())
                        // .add("replicas", getReplicas())
                        // .add("queued",getQueued())
                .add("used", getUsed())
                .add("user", getUser())
                .add("path", getPath())
                .add("max", getMax())
                .add("up", isUp())
                .add("dead", isDead())
                .add("readOnly", isReadOnly())
                .add("diskReadOnly", isDiskReadOnly())
                .toString();
    }

    //
    // generic getters / setters
    //

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public double getHistQueueSize() {
        return histQueueSize;
    }

    public void setHistQueueSize(double size) {
        this.histQueueSize = size;
    }

    public double getHistWaitTime() {
        return histWaitTime;
    }

    public void setHistWaitTime(double seconds) {
        this.histWaitTime = seconds;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean isUp() {
        return up;
    }

    public void setUp(boolean up) {
        this.up = up;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getUptime() {
        return uptime;
    }

    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    public JobKey[] getRunning() {
        return running;
    }

    public void setRunning(JobKey[] running) {
        this.running = running;
    }

    // Needed for serialization!
    public JobKey[] getReplicating() {
        return replicating;
    }

    public void setReplicating(JobKey[] replicating) {
        this.replicating = replicating;
    }

    // Needed for serialization!
    public JobKey[] getBackingup() {
        return backingup;
    }

    public void setBackingup(JobKey[] backingup) {
        this.backingup = backingup;
    }

    public void setReplicas(JobKey[] replicas) {
        this.replicas = replicas;
    }

    public JobKey[] getReplicas() {
        return replicas;
    }

    public void setStopped(JobKey[] stopped) {
        this.stopped = stopped;
    }

    public JobKey[] getStopped() {
        return stopped;
    }

    public JobKey[] getQueued() {
        return queued;
    }

    public void setQueued(JobKey[] queued) {
        this.queued = queued;
    }

    public HostCapacity getUsed() {
        return used;
    }

    public void setUsed(HostCapacity used) {
        this.used = used;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public HostCapacity getMax() {
        return max;
    }

    public void setMax(HostCapacity max) {
        this.max = max;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isDiskReadOnly() {
        return this.diskReadOnly;
    }

    public void setDiskReadOnly(boolean diskReadOnly) {
        this.diskReadOnly = diskReadOnly;
    }

    public int getAvailableTaskSlots() {
        return availableTaskSlots;
    }

    public void setAvailableTaskSlots(int availableTaskSlots) {
        this.availableTaskSlots = availableTaskSlots;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public double getMeanActiveTasks() {
        return meanActiveTasks;
    }

    public void setMeanActiveTasks(double meanActiveTasks) {
        this.meanActiveTasks = meanActiveTasks;
    }

    public String getMinionTypes() {
        return minionTypes;
    }

    public void setMinionTypes(String minionTypes) {
        this.minionTypes = minionTypes;
    }

    public JobKey[] getIncompleteReplicas() {
        return incompleteReplicas;
    }

    public void setIncompleteReplicas(JobKey[] incompleteReplicas) {
        this.incompleteReplicas = incompleteReplicas;
    }

    public int getMaxTaskSlots() {
        return maxTaskSlots;
    }

    public void setMaxTaskSlots(int maxTaskSlots) {
        this.maxTaskSlots = maxTaskSlots;
    }
}
