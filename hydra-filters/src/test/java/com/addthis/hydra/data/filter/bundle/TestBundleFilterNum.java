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


import com.addthis.bundle.core.Bundle;
import com.addthis.bundle.core.list.ListBundle;
import com.addthis.bundle.value.ValueArray;
import com.addthis.bundle.value.ValueFactory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestBundleFilterNum {

    @Test
    public void testAdd() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("c0,n3,add,v1,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create(3));
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(4));
        bfn.filter(bundle);
        assertEquals(bundle.getValue(bundle.getFormat().getField("c1")).toString(), "6");
    }

    @Test
    public void testMult() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("c0,n3,*,v1,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create(3));
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(4));
        bfn.filter(bundle);
        assertEquals("9", bundle.getValue(bundle.getFormat().getField("c1")).toString());
    }

    @Test
    public void testVectorOps() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("n1,n2,n3,n4,vector,*,v0,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create(0));
        bfn.filter(bundle);
        assertEquals("24", bundle.getValue(bundle.getFormat().getField("c0")).toString());

        bfn = new BundleFilterNum().setDefine("n1,n2,n3,n4,vector,+,v0,set");
        bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create(0));
        bfn.filter(bundle);
        assertEquals("10", bundle.getValue(bundle.getFormat().getField("c0")).toString());

        bfn = new BundleFilterNum().setDefine("n1,n2,n3,n4,vector,min,v0,set");
        bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create(0));
        bfn.filter(bundle);
        assertEquals("1", bundle.getValue(bundle.getFormat().getField("c0")).toString());

        bfn = new BundleFilterNum().setDefine("n1,n2,n3,n4,vector,max,v0,set");
        bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create(0));
        bfn.filter(bundle);
        assertEquals("4", bundle.getValue(bundle.getFormat().getField("c0")).toString());

    }


    @Test
    public void testInsertArrayString() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("c0,mean,v1,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c0"), ValueFactory.create("1,2,3,4,5"));
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(0.0));
        bfn.filter(bundle);
        assertEquals(ValueFactory.create(3.0), bundle.getValue(bundle.getFormat().getField("c1")));
    }

    @Test
    public void testInsertArrayValue() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("a0,mean,v1,set");
        Bundle bundle = new ListBundle();
        ValueArray array = ValueFactory.createArray(5);
        array.add(ValueFactory.create(1));
        array.add(ValueFactory.create(2));
        array.add(ValueFactory.create(3));
        array.add(ValueFactory.create(4));
        array.add(ValueFactory.create(5));
        bundle.setValue(bundle.getFormat().getField("c0"), array);
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(0.0));
        bfn.filter(bundle);
        assertEquals(ValueFactory.create(3.0), bundle.getValue(bundle.getFormat().getField("c1")));
    }

    @Test
    public void testMean() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("n2:3:5:7:11:13:17:19,mean,v0,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(-1));
        bfn.filter(bundle);
        assertEquals("9.625", bundle.getValue(bundle.getFormat().getField("c1")).toString());
    }

    @Test
    public void testVariance() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("n2:3:5:7:11:13:17:19,variance,v0,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(-1));
        bfn.filter(bundle);
        assertEquals("35.734375", bundle.getValue(bundle.getFormat().getField("c1")).toString());
    }

    @Test
    public void testPop() {
        BundleFilterNum bfn = new BundleFilterNum().setDefine("n1:2:3,pop,v0,set");
        Bundle bundle = new ListBundle();
        bundle.setValue(bundle.getFormat().getField("c1"), ValueFactory.create(-1));
        bfn.filter(bundle);
        assertEquals("2", bundle.getValue(bundle.getFormat().getField("c1")).toString());
    }


}
