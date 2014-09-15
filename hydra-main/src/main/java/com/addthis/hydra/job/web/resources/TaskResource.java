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
package com.addthis.hydra.job.web.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;

import com.addthis.codec.json.CodecJSON;
import com.addthis.hydra.job.Job;
import com.addthis.hydra.job.JobTask;
import com.addthis.hydra.job.spawn.Spawn;
import com.addthis.maljson.JSONObject;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Path("/task")
public class TaskResource {

    private static Logger log = LoggerFactory.getLogger(TaskResource.class);

    private final Spawn spawn;

    private static final String defaultUser = "UNKNOWN_USER";

    public TaskResource(Spawn spawn) {
        this.spawn = spawn;
    }

    @GET
    @Path("/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startTask(@QueryParam("job") Optional<String> jobId,
            @QueryParam("task") Optional<Integer> task) {
//      emitLogLineForAction(kv, "start task " + job + "/" + task);
        try {
            spawn.startTask(jobId.or(""), task.or(-1), true, true, false);
            return Response.ok().build();
        } catch (Exception ex) {
            return Response.serverError().entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopTask(@QueryParam("job") Optional<String> jobId,
            @QueryParam("task") Optional<Integer> task) {
//      emitLogLineForAction(kv, "stop task " + job + "/" + task);
        try {
            spawn.stopTask(jobId.or(""), task.or(-1));
            return Response.ok().build();
        } catch (Exception ex) {
            return Response.serverError().entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("/kill")
    @Produces(MediaType.APPLICATION_JSON)
    public Response killTask(@QueryParam("job") Optional<String> jobId,
            @QueryParam("task") Optional<Integer> task) {
//      emitLogLineForAction(kv, "kill task " + job + "/" + task);
        try {
            spawn.killTask(jobId.or(""), task.or(-1));
            return Response.ok().build();
        } catch (Exception ex) {
            return Response.serverError().entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("/get")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTask(@QueryParam("job") Optional<String> jobId,
            @QueryParam("task") Optional<Integer> taskId) {
        try {
            Job job = spawn.getJob(jobId.get());
            List<JobTask> tasks = job.getCopyOfTasks();
            for (JobTask task : tasks) {
                if (task.getTaskID() == taskId.get()) {
                    JSONObject json = CodecJSON.encodeJSON(task);
                    return Response.ok(json.toString()).build();
                }
            }
            return Response.status(Response.Status.NOT_FOUND).entity("Task with id " + taskId.get() + " was not found for job " + jobId.get()).build();
        } catch (Exception ex) {
            return Response.serverError().entity(ex.getMessage()).build();
        }
    }
}
