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
package com.addthis.hydra.job.preprocess;

import java.util.ArrayList;
import java.util.List;

import com.addthis.hydra.job.Spawn;

import com.google.common.base.Joiner;

public class JobAliasExpander implements JobConfigExpander {

    private static final Joiner joiner = Joiner.on(' ').skipNulls();

    @Override
    public String expand(Spawn spawn, String jobId, List<String> tokens) {
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            List<String> jobs = spawn.aliasToJobs(token);
            if (jobs != null) {
                result.add(joiner.join(jobs));
            }
        }
        return joiner.join(result);
    }
}
