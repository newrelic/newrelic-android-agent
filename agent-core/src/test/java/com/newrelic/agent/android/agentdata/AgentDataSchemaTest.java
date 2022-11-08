/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.BoolSessionAttribute;
import com.newrelic.mobile.fbs.DoubleSessionAttribute;
import com.newrelic.mobile.fbs.LongSessionAttribute;
import com.newrelic.mobile.fbs.StringSessionAttribute;
import com.newrelic.mobile.fbs.hex.*;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class AgentDataSchemaTest {

    @Test
    public void basicSchema() {
        final FlatBufferBuilder flat = new FlatBufferBuilder();

        int osNameAttribute = StringSessionAttribute.createStringSessionAttribute(flat,
                flat.createString("osName"),
                flat.createString("Android")
        );

        int[] stringSessionAttributeOffsets = { osNameAttribute };

        int diskFreeAttribute = LongSessionAttribute.createLongSessionAttribute(flat, flat.createString("diskAvailable"), 34237492342871632L);
        int[] longSessionAttributeOffsets = { diskFreeAttribute };


        int loggedInAttribute = BoolSessionAttribute.createBoolSessionAttribute(flat, flat.createString("loggedIn"), true);
        int[] booleanSessionAttributeOffsets = { loggedInAttribute };


        int temperatureAttribute = DoubleSessionAttribute.createDoubleSessionAttribute(flat, flat.createString("temperature"), 89.3);
        int[] doubleSessionAttributeOffsets = { temperatureAttribute };

        final int hexSessionId = flat.createString("abcd-1234");
        final int hexMessage = flat.createString("message");

        HandledException.startHandledException(flat);
        HandledException.addSessionId(flat,hexSessionId);
        HandledException.addMessage(flat, hexMessage);

        int[] hexOffsets = { HandledException.endHandledException(flat) };

        int hexVector = HexAgentData.createHandledExceptionsVector(flat, hexOffsets);

        int stringSessionAttributesVector = HexAgentData.createStringAttributesVector(flat, stringSessionAttributeOffsets);
        int longSessionAttributesVector = HexAgentData.createLongAttributesVector(flat, longSessionAttributeOffsets);
        int booleanSessionAttributesVector = HexAgentData.createBoolAttributesVector(flat, booleanSessionAttributeOffsets);
        int doubleSessionAttributesVector = HexAgentData.createDoubleAttributesVector(flat, doubleSessionAttributeOffsets);

        HexAgentData.startHexAgentData(flat);
        HexAgentData.addHandledExceptions(flat, hexVector);
        HexAgentData.addStringAttributes(flat, stringSessionAttributesVector);
        HexAgentData.addLongAttributes(flat, longSessionAttributesVector);
        HexAgentData.addBoolAttributes(flat, booleanSessionAttributesVector);
        HexAgentData.addDoubleAttributes(flat, doubleSessionAttributesVector);

        int agentDataOffset = HexAgentData.endHexAgentData(flat);
        flat.finish(agentDataOffset);


        final ByteBuffer byteBuffer = flat.dataBuffer();
        assert byteBuffer != null;


        byte[] byteArray = byteBuffer.array();
        HexAgentData agentData = HexAgentData.getRootAsHexAgentData(byteBuffer);

        assert agentData != null;

        HandledException h0 = agentData.handledExceptions(0);

        assert h0 != null;
        Assert.assertEquals("abcd-1234", h0.sessionId());
        Assert.assertEquals("osName", agentData.stringAttributes(0).name());
        Assert.assertEquals(true, agentData.boolAttributes(0).value());
        Assert.assertEquals(34237492342871632L, agentData.longAttributes(0).value());
        Assert.assertEquals(89.3, agentData.doubleAttributes(0).value(), 0.00001);
    }
}