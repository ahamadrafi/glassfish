/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.main.admin.test.rest;

import jakarta.ws.rs.core.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author jasonlee
 */
public class JdbcITest extends RestTestBase {

    @Test
    public void testReadingPoolEntity() {
        Map<String, String> entity = getEntityValues(managementClient.get(URL_JDBC_CONNECTION_POOL + "/__TimerPool"));
        assertEquals("__TimerPool", entity.get("name"));
    }


    @Test
    public void testCreateAndDeletePool() {
        String poolName = "TestPool" + generateRandomString();
        Map<String, String> params = new HashMap<>();
        params.put("name", poolName);
        params.put("datasourceClassname", "org.apache.derby.jdbc.ClientDataSource");
        Response response = managementClient.post(URL_JDBC_CONNECTION_POOL, params);
        assertEquals(200, response.getStatus());

        Map<String, String> entity = getEntityValues(managementClient.get(URL_JDBC_CONNECTION_POOL + "/" + poolName));
        assertThat(entity, aMapWithSize(greaterThan(40)));

        response = managementClient.delete(URL_JDBC_CONNECTION_POOL + "/" + poolName, Map.of());
        assertEquals(200, response.getStatus());

        response = managementClient.get(URL_JDBC_CONNECTION_POOL + "/" + poolName);
        assertEquals(404, response.getStatus());
    }


    @Test
    public void testBackslashValidation() {
        String poolName = "TestPool\\" + generateRandomString();
        String encodedPoolName = URLEncoder.encode(poolName, StandardCharsets.UTF_8);
        Map<String, String> params = new HashMap<>();
        params.put("name", poolName);
        params.put("poolName", "DerbyPool");

        Response response = managementClient.post(URL_JDBC_RESOURCE, params);
        assertEquals(500, response.getStatus());

        Response responseGet = managementClient.get(URL_JDBC_CONNECTION_POOL + "/" + encodedPoolName);
        assertEquals(500, response.getStatus());
        Map<String, String> entity = getEntityValues(responseGet);
        assertNull(entity);

        response = managementClient.delete("/" + encodedPoolName, Map.of());
        assertEquals(500, response.getStatus());

        response = managementClient.get(URL_JDBC_CONNECTION_POOL + "/" + encodedPoolName);
        assertEquals(500, response.getStatus());
    }


    @Test
    public void createDuplicateResource() {
        final String resourceName = "jdbc/__default";
        Map<String, String> params = Map.of("id", resourceName, "poolName", "DerbyPool");
        Response response = managementClient.post(URL_JDBC_RESOURCE, params);
        assertEquals(500, response.getStatus());
    }


    @Test
    public void createDuplicateConnectionPool() {
        final String poolName = "DerbyPool";
        Map<String, String> params = Map.of("id", poolName, "datasourceClassname",
            "org.apache.derby.jdbc.ClientDataSource");
        Response response = managementClient.post(URL_JDBC_CONNECTION_POOL, params);
        assertEquals(500, response.getStatus());
    }
}
