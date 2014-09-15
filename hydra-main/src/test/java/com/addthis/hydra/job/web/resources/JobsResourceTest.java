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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;

import com.addthis.basis.kv.KVPairs;

import com.addthis.hydra.job.Job;
import com.addthis.hydra.job.spawn.Spawn;
import com.addthis.hydra.job.web.JobRequestHandler;
import com.addthis.hydra.job.web.jersey.User;

import org.junit.Before;
import org.junit.Test;

public class JobsResourceTest {

    private Spawn spawn;
    private JobRequestHandler requestHandler;
    private JobsResource resource;
    private User user = new User("megatron", "whatever");
    private KVPairs kv;

    @Before
    public void setUp() {
        // mocks and stubs
        spawn = mock(Spawn.class);
        requestHandler = mock(JobRequestHandler.class);

        resource = new JobsResource(spawn, requestHandler);
        kv = new KVPairs();
    }

    @Test
    public void saveJob() throws Exception {
        // stub spawn calls
        Job job = new Job("new_job_id", "megatron");
        when(requestHandler.createOrUpdateJob(kv, "megatron")).thenReturn(job);
        Response response = resource.saveJob(kv, user);
        assertEquals(200, response.getStatus());
        verifyZeroInteractions(spawn);
    }

    @Test
    public void saveJob_BadParam() throws Exception {
        when(requestHandler.createOrUpdateJob(kv, "megatron")).thenThrow(new IllegalArgumentException("bad param"));
        Response response = resource.saveJob(kv, user);
        assertEquals(400, response.getStatus());
        verifyZeroInteractions(spawn);
    }

    @Test
    public void saveJob_InternalError() throws Exception {
        when(requestHandler.createOrUpdateJob(kv, "megatron")).thenThrow(new Exception("internal error"));
        Response response = resource.saveJob(kv, user);
        assertEquals(500, response.getStatus());
        verifyZeroInteractions(spawn);
    }
}
