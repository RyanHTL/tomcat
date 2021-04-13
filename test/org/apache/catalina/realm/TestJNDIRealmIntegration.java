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
package org.apache.catalina.realm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.juli.logging.LogFactory;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.AddRequest;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;

@RunWith(Parameterized.class)
public class TestJNDIRealmIntegration {

    private static final String USER_PATTERN = "cn={0},ou=people,dc=example,dc=com";
    private static final String USER_SEARCH = "cn={0}";
    private static final String USER_BASE = "ou=people,dc=example,dc=com";
    private static final String ROLE_SEARCH_A = "member={0}";
    private static final String ROLE_SEARCH_B = "member=cn={1},ou=people,dc=example,dc=com";

    private static InMemoryDirectoryServer ldapServer;

    @Parameterized.Parameters(name = "{index}: user[{3}], pwd[{4}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        for (String roleSearch : new String[] { ROLE_SEARCH_A, ROLE_SEARCH_B }) {
            addUsers(USER_PATTERN, null, null, roleSearch, parameterSets);
            addUsers(null, USER_SEARCH, USER_BASE, roleSearch, parameterSets);
        }
        return parameterSets;
    }


    private static void addUsers(String userPattern, String userSearch, String userBase, String roleSearch,
            List<Object[]> parameterSets) {
        parameterSets.add(new Object[] { userPattern, userSearch, userBase, roleSearch,
                "test", "test", new String[] {"TestGroup"} });
        parameterSets.add(new Object[] { userPattern, userSearch, userBase, roleSearch,
                "t;", "test", new String[] {"TestGroup"} });
        parameterSets.add(new Object[] { userPattern, userSearch, userBase, roleSearch,
                "t*", "test", new String[] {"TestGroup"} });
    }


    @Parameter(0)
    public String realmConfigUserPattern;
    @Parameter(1)
    public String realmConfigUserSearch;
    @Parameter(2)
    public String realmConfigUserBase;
    @Parameter(3)
    public String realmConfigRoleSearch;
    @Parameter(4)
    public String username;
    @Parameter(5)
    public String credentials;
    @Parameter(6)
    public String[] groups;

    @Test
    public void testAuthenication() throws Exception {
        JNDIRealm realm = new JNDIRealm();
        realm.containerLog = LogFactory.getLog(TestJNDIRealmIntegration.class);

        realm.setConnectionURL("ldap://localhost:" + ldapServer.getListenPort());
        realm.setUserPattern(realmConfigUserPattern);
        realm.setUserSearch(realmConfigUserSearch);
        realm.setUserBase(realmConfigUserBase);
        realm.setUserRoleAttribute("cn");
        realm.setRoleName("cn");
        realm.setRoleBase("ou=people,dc=example,dc=com");
        realm.setRoleSearch(realmConfigRoleSearch);

        GenericPrincipal p = (GenericPrincipal) realm.authenticate(username, credentials);

        Assert.assertNotNull(p);
        Assert.assertEquals(username, p.name);

        Set<String> actualGroups = new HashSet<>(Arrays.asList(p.getRoles()));
        Set<String> expectedGroups  = new HashSet<>(Arrays.asList(groups));

        Assert.assertEquals(expectedGroups.size(), actualGroups.size());
        Set<String> tmp = new HashSet<>();
        tmp.addAll(expectedGroups);
        tmp.removeAll(actualGroups);
        Assert.assertEquals(0, tmp.size());
    }


    @BeforeClass
    public static void createLDAP() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.addAdditionalBindCredentials("cn=admin", "password");
        ldapServer = new InMemoryDirectoryServer(config);

        ldapServer.startListening();

        try (LDAPConnection conn =  ldapServer.getConnection()) {

            AddRequest addBase = new AddRequest(
                    "dn: dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: domain",
                    "dc: example");
            LDAPResult result = conn.processOperation(addBase);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addPeople = new AddRequest(
                    "dn: ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: organizationalUnit");
            result = conn.processOperation(addPeople);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addUserTest = new AddRequest(
                    "dn: cn=test,ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "cn: test",
                    "sn: Test",
                    "userPassword: test");
            result = conn.processOperation(addUserTest);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addUserTestSemicolon = new AddRequest(
                    "dn: cn=t\\;,ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "cn: t\\;",
                    "sn: Tsemicolon",
                    "userPassword: test");
            result = conn.processOperation(addUserTestSemicolon);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addUserTestAsterisk = new AddRequest(
                    "dn: cn=t\\*,ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "cn: t\\*",
                    "sn: Tasterisk",
                    "userPassword: test");
            result = conn.processOperation(addUserTestAsterisk);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addGroupTest = new AddRequest(
                    "dn: cn=TestGroup,ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: groupOfNames",
                    "cn: TestGroup",
                    "member: cn=test,ou=people,dc=example,dc=com",
                    "member: cn=t\\;,ou=people,dc=example,dc=com",
                    "member: cn=t\\*,ou=people,dc=example,dc=com");
            result = conn.processOperation(addGroupTest);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());
        }
    }


    @AfterClass
    public static void destroyLDAP() {
        ldapServer.shutDown(true);
    }
}