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
package com.addthis.hydra.data.filter.value;

import com.addthis.bundle.value.ValueFactory;
import com.addthis.bundle.value.ValueObject;
import com.addthis.codec.annotations.FieldConfig;

/**
 * This {@link ValueFilter ValueFilter} <span class="hydra-summary">returns a constant value</span>.
 * <p/>
 * <p>Example:</p>
 * <pre>
 *      {op:"field", from:"TYPE", filter:{op:"set", value:"foo"}},
 * </pre>
 *
 * @user-reference
 * @hydra-name set
 */
public class ValueFilterSet extends ValueFilter {

    /**
     * The output value.
     */
    @FieldConfig(codable = true)
    private String value;

    private ValueObject cache;

    @Override
    public ValueObject filterValue(ValueObject v) {
        if (cache == null && value != null) {
            cache = ValueFactory.create(value);
        }
        return cache;
    }

}
