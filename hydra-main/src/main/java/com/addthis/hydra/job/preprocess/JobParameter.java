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

import com.addthis.codec.Codec;


/**
 * config-defined templated parameter
 */
public final class JobParameter implements Codec.Codable, Cloneable {

    @Codec.Set(codable = true)
    private String name;
    @Codec.Set(codable = true)
    private String value;
    @Codec.Set(codable = true)
    private String defaultValue;

    public JobParameter() {
    }

    public JobParameter(String name, String value, String defaultValue) {
        this.name = name;
        this.value = value;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }


    public String getParamString() {
        return "%[" + (defaultValue != null ? name + ":" + defaultValue : name) + "]%";
    }
}
