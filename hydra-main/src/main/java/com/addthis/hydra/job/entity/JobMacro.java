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
package com.addthis.hydra.job.entity;

import com.addthis.basis.util.JitterClock;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.codables.Codable;
import com.addthis.codec.json.CodecJSON;
import com.addthis.maljson.JSONObject;

import com.fasterxml.jackson.annotation.JsonAutoDetect;


/**
 * config templatable regions used in jobs
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE,
                setterVisibility = JsonAutoDetect.Visibility.NONE)
public final class JobMacro implements Codable, Cloneable {

    @FieldConfig
    private String description;
    @FieldConfig
    private String macro;
    @FieldConfig
    private long modified;
    @FieldConfig
    private String owner;

    public JobMacro() {
    }

    public JobMacro(String owner, String description, String macro) {
        this.owner = owner;
        this.description = description;
        this.macro = macro;
        this.modified = JitterClock.globalTime();
    }


    public String getOwner() {
        return owner;
    }

    public String getDescription() {
        return description;
    }

    public String getMacro() {
        return macro;
    }

    public long getModified() {
        return modified;
    }

    public JSONObject toJSON() throws Exception {
        return CodecJSON.encodeJSON(this);
    }
}
