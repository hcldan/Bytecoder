/*
 * Copyright 2017 Mirko Sertic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mirkosertic.bytecoder.classlib.java.lang;

import de.mirkosertic.bytecoder.classlib.org.junit.TAssert;
import de.mirkosertic.bytecoder.unittest.BytecoderUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BytecoderUnitTestRunner.class)
public class TFloatTest {

    @Test
    public void testCompare() {
        TAssert.assertEquals(TFloat.compare(10f, 20f), -1, 0);
        TAssert.assertEquals(TFloat.compare(10f, 10f), 0, 0);
        TAssert.assertEquals(TFloat.compare(20f, 10f), 1, 0);
    }
}