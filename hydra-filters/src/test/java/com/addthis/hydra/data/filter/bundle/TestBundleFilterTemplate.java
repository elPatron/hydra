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
package com.addthis.hydra.data.filter.bundle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class TestBundleFilterTemplate {

    @Test
    public void fieldTest() {
        BundleFilterTemplate bft = BundleFilterTemplate.create(new String[]{"abc", "{{tok1}}", "def", "{{tok2}}", "ghi{{notatoken}}jkl"}, "out");
        MapBundle bundle = MapBundle.createBundle(
                new String[]{"abc", "123", "tok1", "1kot", "tok2", "2kot", "notatoken", "fooledya", "out", "killme"});
        bft.filter(bundle);
        assertEquals(bundle.get("out"), "abc1kotdef2kotghi{{notatoken}}jkl");
    }
}
