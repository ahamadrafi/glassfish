/*
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.diagnostics.context.impl;

import java.util.EnumSet;

import org.glassfish.contextpropagation.Location;
import org.glassfish.contextpropagation.PropagationMode;
import org.glassfish.contextpropagation.View;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Verifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@Disabled("Incompatible Jmockit 1.49 and JaCoCo 0.8.7, causes ArrayIndexOutOfBoundsException")
public class ContextImplUnitTest {

    /**
     * Test that the Location field of ContextImpl uses the Location
     * object used at construction and that the Location returned from the
     * ContextImpl does not then change over the lifetime of the ContextImpl.
     */
    @Test
    public void testConstructorsLocation(
        @Mocked final Location mockedLocation,
        @Mocked final View mockedView) {

        final String mockedLocationIdReturnValue = "mockedLocationIdReturnValue";
        final String mockedOriginReturnValue = "mockedOriginReturnValue";

        new MockUp<Location>() {
            @Mock
            public String getLocationId() {
                return mockedLocationIdReturnValue;
            }


            @Mock
            public String getOrigin() {
                return mockedOriginReturnValue;
            }
        };

        ContextImpl contextImpl = new ContextImpl(mockedView, mockedLocation);

        Location location1 = contextImpl.getLocation();
        assertSame(mockedLocation, location1,
            "Location from contextImpl.getLocation() should be the instance passed in on construction.");

        // On the face of is these next two assertions seem perfectly reasonable
        // but in reality they prove nothing regarding the behaviour of the
        // org.glassfish.diagnostics.context.impl code, but rather
        // verify that the  mocking framework is doing it's job: the getLocationId
        // and getOrigin methods are overridden by the mock framework to
        // return the values above, they are not returning state from the
        // mockedLocation object itself.
        assertEquals(
            location1.getLocationId(), mockedLocationIdReturnValue,
            "LocationId from contextImpl.getLocation() should be the locationId value from the location used"
            + " when constructing the ContextImpl.");
        assertEquals(location1.getOrigin(), mockedOriginReturnValue,
            "Origin from contextImpl.getOrigin() should be the origin value from the location used"
            + " when constructing the ContextImpl.");

        Location location2 = contextImpl.getLocation();
        assertSame(mockedLocation, location2,
            "Location from contextImpl.getLocation() should still be the instance passed in on construction.");
    }


    /**
     * Test that the put operations on an instance of ContextImpl delegate
     * as expected to the View object used in construction.
     */
    @Test
    public void testDelegationOfPut(
        @Mocked final Location mockedLocation,
        @Mocked final View mockedView){

        ContextImpl contextImpl = new ContextImpl(mockedView, mockedLocation);

        contextImpl.put("KeyForString-Value1-true", "Value1", true);
        contextImpl.put("KeyForString-Value2-false", "Value2", false);

        contextImpl.put("KeyForNumber-5-true", 5, true);
        contextImpl.put("KeyForNumber-7-false", 7, false);

        new Verifications(){{
            mockedView.put("KeyForString-Value1-true", "Value1", EnumSet.of(
                PropagationMode.THREAD,
                PropagationMode.RMI,
                PropagationMode.JMS_QUEUE,
                PropagationMode.SOAP,
                PropagationMode.MIME_HEADER,
                PropagationMode.ONEWAY));
            mockedView.put("KeyForString-Value2-false", "Value2", EnumSet.of(
                PropagationMode.LOCAL));

            mockedView.put("KeyForNumber-5-true", Integer.valueOf(5), EnumSet.of(
                PropagationMode.THREAD,
                PropagationMode.RMI,
                PropagationMode.JMS_QUEUE,
                PropagationMode.SOAP,
                PropagationMode.MIME_HEADER,
                PropagationMode.ONEWAY));
            mockedView.put("KeyForNumber-7-false", Integer.valueOf(7), EnumSet.of(
                PropagationMode.LOCAL));
        }};

    }

    /**
     * Test that the get operation on an instance of ContextImpl delegates
     * as expected to the View object used in construction.
     */
    @Test
    public void testDelegationOfGet(
        @Mocked final Location mockedLocation,
        @Mocked final View mockedView){

        final String key = "testDelegationOfGet-Key1";
        final String expectedValueOfKey1 = "testDelegationOfGet-Value1";
        ContextImpl contextImpl = new ContextImpl(mockedView, mockedLocation);

        new Expectations(){

            // We expect get to be called on the view, and we'll
            // instruct the mocking framework to return expectedValueOfKey1
            // so that we can also verify that contextImpl returns it.
            View expectationsRefViewVariable = mockedView;
            {
                expectationsRefViewVariable.get(key);
                returns(expectedValueOfKey1, null);
            }
        };

        assertEquals(expectedValueOfKey1, contextImpl.get(key),
            "Value returned from contextImpl.get(\"" + key + "\") is not the value expected.");
    }

}
