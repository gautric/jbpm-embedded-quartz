/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.a.g.jbpm.embedded;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.ResultSet;
import java.util.HashMap;

import javax.sql.DataSource;

import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.runtime.manager.impl.jpa.EntityManagerFactoryManager;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import net.a.g.jbpm.embedded.JBPMApplication;
import net.a.g.jbpm.embedded.listeners.CountDownLatchEventListener;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.runtime.conf.RuntimeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = { JBPMApplication.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TimerQuartzEmbeddedTest {
	
    private static final Logger LOGGER = LoggerFactory.getLogger(TimerQuartzEmbeddedTest.class);


    
    @Value("${test.artifactId}")
    private String ARTIFACT_ID;
    @Value("${test.groupId}")
    private String GROUP_ID ;
    @Value("${test.version}")
    private String VERSION ;

    private KModuleDeploymentUnit unit = null;

    @Autowired
    private ProcessService processService;

    @Autowired
    private RuntimeDataService runtimeDataService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private CountDownLatchEventListener countDownListener;

    @Autowired
    private DataSource dataSource;

    @BeforeClass
    public static void generalSetup() {
        EntityManagerFactoryManager.get().clear();
    }

    @Before
    public void setup() {
        unit = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);
        unit.setStrategy(RuntimeStrategy.PER_REQUEST);
        deploymentService.deploy(unit);
    }

    @After
    public void cleanup() {
        deploymentService.undeploy(unit);
    }

    @Test(timeout = 60000)
    public void testProcessStartWithTimer10S() throws Exception {
        countDownListener.configure("TimerTestProcess", 1);

        HashMap<String,Object> param = new HashMap<String,Object>();

        param.put("integerIn", 123);
        param.put("booleanIn", true);
        param.put("stringIn", "Test Quartz 10s");
        param.put("timerIn", "PT10S");

        long processInstanceId = processService.startProcess(unit.getIdentifier(), "TimerTestProcess", param);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId > 0);
        assertQRTZCount(1);

        countDownListener.getCountDown().await();
        Thread.sleep(2 * 1000);
        // make sure it was quartz thread that completed the process as the process ends
        // after timer expiration
        assertTrue(countDownListener.getExecutingThread().startsWith("QuartzScheduler"));

        ProcessInstance pi = processService.getProcessInstance(processInstanceId);
        assertNull(pi);

        ProcessInstanceDesc pid = runtimeDataService.getProcessInstanceById(processInstanceId);
        assertEquals((Integer) pid.getState(), (Integer) 2);
        assertQRTZCount(0);
    }

    @Test(timeout = 45000)
    public void testProcessStartWithTimer15S() throws Exception {
        countDownListener.configure("TimerTestProcess", 1);

        HashMap<String,Object> param = new HashMap<String,Object>();

        param.put("integerIn", 123);
        param.put("booleanIn", true);
        param.put("stringIn", "Test Quartz 15s");
        param.put("timerIn", "PT15S");

        long processInstanceId = processService.startProcess(unit.getIdentifier(), "TimerTestProcess", param);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId > 0);

        ProcessInstanceDesc pid = runtimeDataService.getProcessInstanceById(processInstanceId);
        assertEquals((Integer) pid.getState(), (Integer) 1);

        assertQRTZCount(1);

        countDownListener.getCountDown().await();   
        Thread.sleep(2 * 1000);

        // make sure it was quartz thread that completed the process as the process ends after timer expiration
        assertTrue(countDownListener.getExecutingThread().startsWith("QuartzScheduler"));
        
        ProcessInstance pi = processService.getProcessInstance(processInstanceId);
        assertNull(pi);

        ProcessInstanceDesc pid2 = runtimeDataService.getProcessInstanceById(processInstanceId);
        assertEquals((Integer)pid2.getState(), (Integer)2);

        assertQRTZCount(0);
    }  


    private void assertQRTZCount(int count) throws Exception {

        ResultSet result = dataSource.getConnection().prepareStatement("SELECT COUNT(*) FROM QRTZ_TRIGGERS").executeQuery();
        assertTrue(result.next());
        assertEquals((Integer)count,(Integer)result.getInt(1));
        LOGGER.info("Timer Validation into Quartz Database, --> {} line into QRTZ_TRIGGERS", count);
        

    }
}

