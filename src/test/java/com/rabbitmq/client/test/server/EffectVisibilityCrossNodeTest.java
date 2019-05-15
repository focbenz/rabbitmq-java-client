// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test.server;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import com.rabbitmq.client.test.functional.ClusteredTestBase;

/**
 * From bug 19844 - we want to be sure that publish vs everything else can't
 * happen out of order
 */
public class EffectVisibilityCrossNodeTest extends ClusteredTestBase {
    private final String[] queues = new String[QUEUES];
    private Connection purgeConnection;

    @Override
    protected void createResources() throws IOException, TimeoutException {
        for (int i = 0; i < queues.length ; i++) {
            queues[i] = alternateChannel.queueDeclare("", false, false, true, null).getQueue();
            alternateChannel.queueBind(queues[i], "amq.fanout", "");
        }
        this.purgeConnection = TestUtils.connectionFactory().newConnection();
    }

    @Override
    protected void releaseResources() throws IOException {
        try {
            for (int i = 0; i < queues.length ; i++) {
                alternateChannel.queueDelete(queues[i]);
            }
        } finally {
            TestUtils.close(this.purgeConnection);
        }
    }

    private static final int QUEUES = 5;
    private static final int BATCHES = 500;
    private static final int MESSAGES_PER_BATCH = 10;

    private static final byte[] msg = "".getBytes();

    @Test public void effectVisibility() throws Exception {
        Channel purgeChannel = this.purgeConnection.createChannel();
        channel.confirmSelect();
        for (int i = 0; i < BATCHES; i++) {
            for (int j = 0; j < MESSAGES_PER_BATCH; j++) {
                channel.basicPublish("amq.fanout", "", null, msg);
            }
            channel.waitForConfirmsOrDie(60_000);
            for (int j = 0; j < queues.length ; j++) {
                assertEquals(MESSAGES_PER_BATCH, purgeChannel.queuePurge(queues[j]).getMessageCount());
            }
        }
    }
}
