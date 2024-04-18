/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedSendDynamicProcessorTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    @Test
    public void testManageSendDynamicProcessor() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(2);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "whereto", "foo");
        template.sendBodyAndHeader("direct:start", "Bye World", "whereto", "foo");
        template.sendBodyAndHeader("direct:start", "Hi World", "whereto", "bar");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "mysend");

        // should be on route1
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertTrue(routeId.matches("route[0-9]+"));

        String camelId = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals(context.getManagementName(), camelId);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        String uri = (String) mbeanServer.getAttribute(on, "Uri");
        assertEquals("direct:${header.whereto}", uri);

        Boolean optimised = (Boolean) mbeanServer.getAttribute(on, "Optimised");
        assertFalse(optimised);

        String pattern = (String) mbeanServer.getAttribute(on, "MessageExchangePattern");
        assertNull(pattern);

        TabularData data = (TabularData) mbeanServer.invoke(on, "extendedInformation", null, null);
        assertNotNull(data);
        assertEquals(2, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toD("direct:${header.whereto}").id("mysend");

                from("direct:foo").to("mock:foo");

                from("direct:bar").to("mock:bar");
            }
        };
    }

}
