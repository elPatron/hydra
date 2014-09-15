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
package com.addthis.hydra.job.spawn;

import java.util.HashMap;
import java.util.Map;

import com.addthis.basis.util.JitterClock;

import com.addthis.hydra.job.Job;
import com.addthis.hydra.job.JobState;
import com.addthis.hydra.job.JobTask;
import com.addthis.hydra.job.mq.HostCapacity;
import com.addthis.hydra.job.mq.HostState;

class UpdateEventRunnable implements Runnable {

    private Spawn spawn;
    private final Map<String, Long> events = new HashMap<>();

    public UpdateEventRunnable(Spawn spawn) {this.spawn = spawn;}

    @Override
    public void run() {
        HostCapacity hostmax = new HostCapacity();
        HostCapacity hostused = new HostCapacity();
        synchronized (spawn.monitored) {
            for (HostState hs : spawn.monitored.values()) {
                hostmax.add(hs.getMax());
                hostused.add(hs.getUsed());
            }
        }
        int jobshung = 0;
        int jobrunning = 0;
        int jobscheduled = 0;
        int joberrored = 0;
        int taskallocated = 0;
        int taskbusy = 0;
        int taskerrored = 0;
        int taskqueued = 0;
        long files = 0;
        long bytes = 0;
        spawn.jobLock.lock();
        try {
            for (Job job : spawn.spawnState.jobs.values()) {
                for (JobTask jn : job.getCopyOfTasks()) {
                    switch (jn.getState()) {
                        case ALLOCATED:
                            taskallocated++;
                            break;
                        case BUSY:
                            taskbusy++;
                            break;
                        case ERROR:
                            taskerrored++;
                            break;
                        case IDLE:
                            break;
                        case QUEUED:
                            taskqueued++;
                            break;
                        case QUEUED_HOST_UNAVAIL:
                            taskqueued++;
                            break;
                    }
                    files += jn.getFileCount();
                    bytes += jn.getByteCount();
                }
                switch (job.getState()) {
                    case IDLE:
                        break;
                    case RUNNING:
                        jobrunning++;
                        if (job.getStartTime() != null && job.getMaxRunTime() != null &&
                            (JitterClock.globalTime() - job.getStartTime() > job.getMaxRunTime() * 2)) {
                            jobshung++;
                        }
                        break;
                    case SCHEDULED:
                        jobscheduled++;
                        break;
                }
                if (job.getState() == JobState.ERROR) {
                    joberrored++;
                }
            }
        } finally {
            spawn.jobLock.unlock();
        }
        events.clear();
        events.put("time", System.currentTimeMillis());
        events.put("hosts", (long) spawn.monitored.size());
        events.put("commands", (long) spawn.getJobCommandManager().size());
        events.put("macros", (long) spawn.getJobMacroManager().size());
        events.put("jobs", (long) spawn.spawnState.jobs.size());
        events.put("cpus", (long) hostmax.getCpu());
        events.put("cpus_used", (long) hostused.getCpu());
        events.put("mem", (long) hostmax.getMem());
        events.put("mem_used", (long) hostused.getMem());
        events.put("io", (long) hostmax.getIo());
        events.put("io_used", (long) hostused.getIo());
        events.put("jobs_running", (long) jobrunning);
        events.put("jobs_scheduled", (long) jobscheduled);
        events.put("jobs_errored", (long) joberrored);
        events.put("jobs_hung", (long) jobshung);
        events.put("tasks_busy", (long) taskbusy);
        events.put("tasks_allocated", (long) taskallocated);
        events.put("tasks_queued", (long) taskqueued);
        events.put("tasks_errored", (long) taskerrored);
        events.put("files", files);
        events.put("bytes", bytes);
        spawn.spawnFormattedLogger.periodicState(events);
        Spawn.runningTaskCount.set(taskbusy);
        Spawn.queuedTaskCount.set(taskqueued);
        Spawn.failTaskCount.set(taskerrored);
        Spawn.runningJobCount.set(jobrunning);
        Spawn.queuedJobCount.set(jobscheduled);
        Spawn.failJobCount.set(joberrored);
        Spawn.hungJobCount.set(jobshung);
    }
}
