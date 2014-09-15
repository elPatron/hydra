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
package com.addthis.hydra.job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.addthis.basis.util.Parameter;

import com.addthis.hydra.job.mq.HostState;
import com.addthis.hydra.job.mq.JobKey;
import com.addthis.hydra.job.spawn.Spawn;
import com.addthis.hydra.job.store.SpawnDataStore;
import com.addthis.maljson.JSONArray;
import com.addthis.maljson.JSONException;
import com.addthis.maljson.JSONObject;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;

import org.apache.commons.lang3.tuple.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class HostFailWorker {

    private static final Logger log = LoggerFactory.getLogger(HostFailWorker.class);
    private final HostFailState hostFailState;
    private AtomicBoolean newAdditions = new AtomicBoolean(false); // True if a host has been recently added to the queue
    private AtomicBoolean obeyTaskSlots = new AtomicBoolean(true); // Whether spawn should honor the max task slots when moving tasks to fail hosts
    private final Spawn spawn;

    // Perform host-failure related operations at a given interval
    private static final long hostFailDelayMillis = Parameter.longValue("host.fail.delay", 15_000);
    // Quiet period between when host is failed in UI and when Spawn begins failure-related operations
    private static final long hostFailQuietPeriod = Parameter.longValue("host.fail.quiet.period", 20_000);

    // Don't rebalance additional tasks if spawn is already rebalancing at least this many.
    private static final int maxMovingTasks = Parameter.intValue("host.fail.maxMovingTasks", 6);
    // Use a smaller max when a disk is being failed, to avoid a 'thundering herds' scenario
    private static final int maxMovingTasksDiskFull = Parameter.intValue("host.fail.maxMovingTasksDiskFull", 2);

    private static final String dataStoragePath = "/spawn/hostfailworker";
    private static final Counter failHostCount = Metrics.newCounter(Spawn.class, "failHostCount");

    // Various keys used to make JSON objects to send to the UI
    private static final String infoHostsKey = "uuids";
    private static final String infoDeadFsKey = "deadFs";
    private static final String infoWarningKey = "warning";
    private static final String infoPrefailCapacityKey = "prefail";
    private static final String infoPostfailCapacityKey = "postfail";
    private static final String infoFatalWarningKey = "fatal";
    private final ScheduledExecutorService executorService;

    public HostFailWorker(Spawn spawn, ScheduledExecutorService executorService) {
        hostFailState = new HostFailState();
        this.spawn = spawn;
        boolean loaded = hostFailState.loadState();
        this.executorService = executorService;
        if (loaded && executorService != null) {
            queueFailNextHost();
        }
        if (executorService != null) {
            executorService.scheduleWithFixedDelay(new FailHostTask(true), hostFailDelayMillis, hostFailDelayMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Mark a series of hosts for failure
     *
     * @param hostIds        A comma-separated list of host uuids
     * @param state      The state of the hosts being failed
     */
    public void markHostsToFail(String hostIds, FailState state) {
        if (hostIds != null) {
            for (String host : hostIds.split(",")) {
                hostFailState.putHost(host, state);
                spawn.sendHostUpdateEvent(spawn.getHostState(host));
            }
            queueFailNextHost();
        }
    }

    /**
     * Retrieve an enum describing whether/how a host has been failed (for programmatic purposes)
     *
     * @param hostId A host uuid to check
     * @return A FailState object describing whether the host has been failed
     */
    public FailState getFailureState(String hostId) {
        return hostFailState.getState(hostId);
    }

    public boolean shouldKickTasks(String hostId) {
        FailState failState = getFailureState(hostId);
        // A Failing_Fs_Okay host is nominally fine for the time being. It should be allowed to run tasks.
        return failState == FailState.ALIVE || failState == FailState.FAILING_FS_OKAY;
    }

    /**
     * Retrieve a human-readable string describing whether/how a host has been failed
     *
     * @param hostId A host uuid to check
     * @param up     Whether the host is up
     * @return A String describing the host's failure state (mainly for the UI)
     */
    public String getFailureStateString(String hostId, boolean up) {
        FailState failState = getFailureState(hostId);
        switch (failState) {
            case ALIVE:
                return up ? "up" : "down";
            case FAILING_FS_DEAD:
                return "queued to fail (fs dead)";
            case FAILING_FS_OKAY:
                return "queued to fail (fs okay)";
            case DISK_FULL:
                return "disk near full; moving tasks off";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Cancel the failure of one or more hosts
     *
     * @param hostIds A comma-separated list of host uuids
     */
    public void removeHostsForFailure(String hostIds) {
        if (hostIds != null) {
            for (String host : hostIds.split(",")) {
                hostFailState.removeHost(host);
                spawn.sendHostUpdateEvent(spawn.getHostState(host));
            }
        }
    }

    /**
     * Decide whether a given host can be failed based on whether other minions in the cluster are up
     *
     * @param failedHostUuid The host to be failed
     * @return True only if there are no down hosts that would need to be up in order to correctly fail the host
     */
    protected boolean checkHostStatesForFailure(String failedHostUuid) {
        Collection<HostState> hostStates = spawn.listHostStatus(null);
        for (HostState hostState : hostStates) {
            if (!failedHostUuid.equals(hostState.getHostUuid()) && shouldBlockHostFailure(ImmutableSet.of(failedHostUuid), hostState)) {
                log.warn("Unable to fail host: " + failedHostUuid + " because one of the minions (" + hostState.getHostUuid() + ") on " + hostState.getHost() + " is currently down.  Retry when all minions are available");
                return false;
            }
        }
        return true;
    }

    /**
     * Fail a host. For any tasks with replicas on that host, move these replicas elsewhere. For any tasks with live copies on the host,
     * promote a replica, then make a new replica somewhere else.
     */
    private void markHostDead(String failedHostUuid) {
        if (failedHostUuid == null || !checkHostStatesForFailure(failedHostUuid)) {
            return;
        }
        spawn.markHostStateDead(failedHostUuid);
        hostFailState.removeHost(failedHostUuid);
        failHostCount.inc();
    }

    /**
     * Before failing host(s), check if a different host needs to be up to perform the failure operation.
     *
     * @param failedHostUUIDs The hosts being failed
     * @param hostState       The host to check
     * @return True if the host is down
     */
    private boolean shouldBlockHostFailure(Set<String> failedHostUUIDs, HostState hostState) {
        if (hostState == null || hostState.isDead() || hostState.isUp()) {
            return false;
        }
        for (JobKey jobKey : hostState.allJobKeys()) // never null due to implementation
        {
            JobTask task = spawn.getTask(jobKey);
            if (task != null && (failedHostUUIDs.contains(task.getHostUUID()) || task.hasReplicaOnHosts(failedHostUUIDs))) {
                // There is a task on a to-be-failed host that has a copy on a host that is down. We cannot fail for now.
                return true;
            }
        }
        return false;
    }

    /**
     * After receiving a host failure request, queue an event to fail that host after a quiet period
     */
    private void queueFailNextHost() {
        if (newAdditions.compareAndSet(false, true)) {
            executorService.schedule(new FailHostTask(false), hostFailQuietPeriod, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Find the next host on the fail queue, considering filesystem-dead hosts first. Perform the correct actions and remove from the queue if appropriate.
     */
    private void failNextHost() {
        Pair<String, FailState> hostToFail = hostFailState.nextHostToFail();
        if (hostToFail != null) {
            String failedHostUuid = hostToFail.getLeft();
            FailState failState = hostToFail.getRight();
            if (failState == FailState.FAILING_FS_DEAD) {
                // File system is dead. Relocate all tasks ASAP.
                markHostDead(failedHostUuid);
                spawn.getSpawnBalancer().fixTasksForFailedHost(spawn.listHostStatus(null), failedHostUuid);
            } else {
                HostState host = spawn.getHostState(failedHostUuid);
                if (host == null) {
                    // Host is gone or has no more tasks. Simply mark it as failed.
                    markHostDead(failedHostUuid);
                    return;
                }
                boolean diskFull = (failState == FailState.DISK_FULL);
                if (!diskFull && spawn.getSettings().getQuiesced()) {
                    // If filesystem is okay, don't do any moves while spawn is quiesced.
                    return;
                }
                int taskMovingMax = diskFull ? maxMovingTasksDiskFull : maxMovingTasks;
                int tasksToMove = taskMovingMax - countRebalancingTasks();
                if (tasksToMove <= 0) {
                    // Spawn is already moving enough tasks; hold off until later
                    return;
                }
                List<JobTaskMoveAssignment> assignments = spawn.getSpawnBalancer().pushTasksOffDiskForFilesystemOkayFailure(host, tasksToMove);
                // Use available task slots to push tasks off the host in question. Not all of these assignments will necessarily be moved.
                spawn.executeReallocationAssignments(assignments, !diskFull && obeyTaskSlots.get());
                if (failState == FailState.FAILING_FS_OKAY && assignments.isEmpty() && host.countTotalLive() == 0) {
                    // Found no tasks on the failed host, so fail it for real.
                    markHostDead(failedHostUuid);
                    spawn.getSpawnBalancer().fixTasksForFailedHost(spawn.listHostStatus(host.getMinionTypes()), failedHostUuid);
                }
            }
        }
    }

    public void setObeyTaskSlots(boolean obey) {
        obeyTaskSlots.set(obey);
    }

    /**
     * Retrieve information about the implications of failing a host, to inform/warn a user in the UI
     *
     * @param hostsToFail The hosts that will be failed
     * @return A JSONObject with various data about the implications of the failure
     */
    public JSONObject getInfoForHostFailure(String hostsToFail, boolean deadFilesystem) throws JSONException {
        if (hostsToFail == null) {
            return new JSONObject();
        }
        HashSet<String> ids = new HashSet<>(Arrays.asList(hostsToFail.split(",")));
        long totalClusterAvail = 0, totalClusterUsed = 0, hostAvail = 0;
        List<String> hostsDown = new ArrayList<>();
        for (HostState host : spawn.listHostStatus(null)) {
            // Sum up disk availability across the entire cluster and across the specified hosts
            if (host.getMax() != null && host.getUsed() != null) {
                if (getFailureState(host.getHostUuid()) == FailState.ALIVE) {
                    totalClusterAvail += host.getMax().getDisk();
                    totalClusterUsed += host.getUsed().getDisk();
                }
                if (ids.contains(host.getHostUuid())) {
                    hostAvail += host.getMax().getDisk();
                }
            }
            if (!ids.contains(host.getHostUuid()) && shouldBlockHostFailure(ids, host)) {
                hostsDown.add(host.getHostUuid() + " on " + host.getHost());
            }
        }
        // Guard against division by zero in the case of unexpected values
        totalClusterAvail = Math.max(1, totalClusterAvail);
        hostAvail = Math.min(totalClusterAvail - 1, hostAvail);
        return constructInfoMessage(hostsToFail, deadFilesystem, (double) (totalClusterUsed) / totalClusterAvail, (double) (totalClusterUsed) / (totalClusterAvail - hostAvail), hostsDown);
    }

    /**
     * Create the info message about host message using some raw values
     *
     * @param prefailCapacity  The capacity the cluster had before the failure
     * @param postfailCapacity The capacity the cluster would have after the failure
     * @param hostsDown        Any hosts that are down that might temporarily prevent failure
     * @return A JSONObject encapsulating the above information.
     * @throws JSONException
     */
    private JSONObject constructInfoMessage(String hostsToFail, boolean deadFilesystem, double prefailCapacity, double postfailCapacity, List<String> hostsDown) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(infoHostsKey, hostsToFail);
        obj.put(infoDeadFsKey, deadFilesystem);
        obj.put(infoPrefailCapacityKey, prefailCapacity);
        obj.put(infoPostfailCapacityKey, postfailCapacity);
        if (Double.isNaN(postfailCapacity)) {
            obj.put(infoFatalWarningKey, "Cannot fail all hosts from a cluster");
        } else if (postfailCapacity >= 1) {
            obj.put(infoFatalWarningKey, "Insufficient cluster disk capacity");
        }
        if (!hostsDown.isEmpty()) {
            obj.put(infoWarningKey, "Some hosts are down. Host failure could be delayed until they return: " + hostsDown);
        }
        return obj;
    }


    /**
     * A simple wrapper around failNextHost that is run by the failExecutor.
     */
    private class FailHostTask implements Runnable {

        private final boolean skipIfNewAdditions;

        public FailHostTask(boolean skipIfNewAdditions) {
            this.skipIfNewAdditions = skipIfNewAdditions;
        }

        @Override
        public void run() {
            updateFullMinions();
            if (skipIfNewAdditions && newAdditions.get()) {
                return;
            }
            try {
                failNextHost();
            } catch (Exception e) {
                log.warn("Exception while failing host: " + e, e);
                } finally {
                newAdditions.set(false);
            }
        }
    }

    public void updateFullMinions() {
        for (HostState hostState : spawn.listHostStatus(null)) {
            if (hostState == null || hostState.isDead() || !hostState.isUp()) {
                continue;
            }
            String hostId = hostState.getHostUuid();
            if (spawn.getSpawnBalancer().isDiskFull(hostState)) {
                markHostsToFail(hostId, HostFailWorker.FailState.DISK_FULL);
            } else if (getFailureState(hostId) == HostFailWorker.FailState.DISK_FULL) {
                // Host was previously full, but isn't anymore. Take it off the disk_full list.
                hostFailState.removeHost(hostId);
            }
        }
    }

    /**
     * A class storing the internal state of the host failure queue. All changes are immediately saved to the SpawnDataStore
     */
    private class HostFailState {
        private final Object hostsToFailByTypeLock = new Object();
        private static final String filesystemDeadKey = "deadFs";
        private static final String filesystemOkayKey = "okayFs";
        private static final String filesystemFullKey = "fullFs";
        private final Set<String> failFsDead;
        private final Set<String> failFsOkay;
        private final Set<String> fsFull;
        private final Map<FailState, Set<String>> hostsToFailByType;

        public HostFailState() {
            synchronized (hostsToFailByTypeLock) {
                failFsDead = new LinkedHashSet<>();
                failFsOkay = new LinkedHashSet<>();
                fsFull = new LinkedHashSet<>();
                hostsToFailByType = ImmutableMap.of(FailState.FAILING_FS_DEAD, failFsDead,
                        FailState.DISK_FULL, fsFull, FailState.FAILING_FS_OKAY, failFsOkay);
            }
        }

        /**
         * Add a new host to the failure queue
         *
         * @param hostId         The host id to add
         * @param failState      The state of the host being failed
         */
        public void putHost(String hostId, FailState failState) {

            synchronized (hostsToFailByTypeLock) {
                if (failFsDead.contains(hostId)) {
                    log.info("Ignoring fs-okay failure of " + hostId + " because it is already being failed fs-dead");
                    return;
                }
                switch (failState) {
                    case FAILING_FS_DEAD:
                        fsFull.remove(hostId);
                        failFsOkay.remove(hostId);
                        failFsDead.add(hostId);
                        break;
                    case FAILING_FS_OKAY:
                        fsFull.remove(hostId);
                        failFsOkay.add(hostId);
                        break;
                    case DISK_FULL:
                        fsFull.add(hostId);
                        failFsOkay.remove(hostId);
                        break;
                    default:
                        log.warn("Unexcepted failState " + failState);
                }
                saveState();
            }
        }

        /**
         * Retrieve the next host to fail
         *
         * @return The uuid of the next host to fail, and whether the file system is dead. If the queue is empty, return null.
         */
        public Pair<String, FailState> nextHostToFail() {
            synchronized (hostsToFailByType) {
                String hostUuid = findFirstHost(failFsDead, false);
                if (hostUuid != null) {
                    return Pair.of(hostUuid, FailState.FAILING_FS_DEAD);
                }
                hostUuid = findFirstHost(fsFull, true);
                if (hostUuid != null) {
                    return Pair.of(hostUuid, FailState.DISK_FULL);
                }
                hostUuid = findFirstHost(failFsOkay, true);
                if (hostUuid != null) {
                    return Pair.of(hostUuid, FailState.FAILING_FS_OKAY);
                }
                return null;
            }
        }

        private String findFirstHost(Set<String> hosts, boolean requireUp) {
            for (String hostUuid : hosts) {
                if (requireUp) {
                    HostState host = spawn.getHostState(hostUuid);
                    if (host != null && !host.isDead() && host.isUp()) {
                        return hostUuid;
                    }
                } else {
                    return hostUuid;
                }
            }
            return null;
        }

        /**
         * Cancel the failure for a host
         *
         * @param hostId The uuid to cancel
         */
        public void removeHost(String hostId) {
            synchronized (hostsToFailByType) {
                failFsDead.remove(hostId);
                failFsOkay.remove(hostId);
                fsFull.remove(hostId);
                saveState();
            }
        }

        /**
         * Load the stored state from the SpawnDataStore
         *
         * @return True if at least one host was loaded
         */
        public boolean loadState() {
            SpawnDataStore spawnDataStore = spawn.getSpawnDataStore();
            if (spawnDataStore == null) {
                return false;
            }
            String raw = spawnDataStore.get(dataStoragePath);
            if (raw == null) {
                return false;
            }
            synchronized (hostsToFailByType) {
                try {
                    JSONObject decoded = new JSONObject(raw);
                    loadHostsFromJSONArray(failFsOkay, decoded.optJSONArray(filesystemOkayKey));
                    loadHostsFromJSONArray(failFsDead, decoded.optJSONArray(filesystemDeadKey));
                    loadHostsFromJSONArray(fsFull, decoded.optJSONArray(filesystemFullKey));
                    return true;
                } catch (Exception e) {
                    log.warn("Failed to load HostFailState: " + e + " raw=" + raw, e);
                }
                return false;
            }
        }

        /**
         * Internal method to convert a JSONArray from the SpawnDataStore to a list of hosts
         */
        private void loadHostsFromJSONArray(Set<String> modified, JSONArray arr) throws JSONException {
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                modified.add(arr.getString(i));
            }
        }

        /**
         * Save the state to the SpawnDataStore
         */
        public void saveState() {
            try {
                synchronized (hostsToFailByType) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(filesystemDeadKey, new JSONArray(failFsDead));
                    jsonObject.put(filesystemOkayKey, new JSONArray(failFsOkay));
                    jsonObject.put(filesystemFullKey, new JSONArray(fsFull));
                    spawn.getSpawnDataStore().put(dataStoragePath, jsonObject.toString());
                }
            } catch (Exception e) {
                log.warn("Failed to save HostFailState: " + e, e);
            }
        }

        /**
         * Get the state of failure for a host
         *
         * @param hostId The host to check
         * @return ALIVE if the host is not being failed; otherwise, a description of the type of failure
         */
        public FailState getState(String hostId) {
            synchronized (hostsToFailByType) {
                if (failFsOkay.contains(hostId)) {
                    return FailState.FAILING_FS_OKAY;
                } else if (failFsDead.contains(hostId)) {
                    return FailState.FAILING_FS_DEAD;
                } else if (fsFull.contains(hostId)) {
                    return FailState.DISK_FULL;
                } else {
                    return FailState.ALIVE;
                }
            }
        }

    }

    private int countRebalancingTasks() {
        int count = 0;
        for (Job job : spawn.listJobs()) {
            if (job.getState() == JobState.REBALANCE) {
                for (JobTask task : job.getCopyOfTasks()) {
                    if (task.getState() == JobTaskState.REBALANCE) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * This enum tracks HostFailWorker's ideas of Host State. Options are:
     * - ALIVE: host is normal
     * - FAILING_FS_DEAD: User has requested that the host be failed immediately. There is a quiet period to allow the
     * queue logic to exit gracefully and to allow user to cancel if there was a mistake.
     * - FAILING_FS_OKAY: User has requested that the host be failed eventually, after safely migrating each task off.
     * - DISK_FULL: HostFailWorker detects hosts that are nearly full on disk, and moves tasks off automatically. Once
     * they return to safer levels, they will go back to ALIVE status.
     */
    public enum FailState {ALIVE, FAILING_FS_DEAD, FAILING_FS_OKAY, DISK_FULL}

}
