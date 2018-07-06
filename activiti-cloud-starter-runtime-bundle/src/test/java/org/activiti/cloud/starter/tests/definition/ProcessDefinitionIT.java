/*
 * Copyright 2018 Alfresco, Inc. and/or its affiliates.
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

package org.activiti.cloud.starter.tests.definition;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.cloud.services.api.model.ProcessDefinitionMeta;
import org.activiti.cloud.starter.tests.util.TestResourceUtil;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.impl.util.IoUtil;
import org.activiti.image.ProcessDiagramGenerator;
import org.activiti.runtime.api.model.ProcessDefinition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.PagedResources;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:application-test.properties")
public class ProcessDefinitionIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProcessDiagramGenerator processDiagramGenerator;

    public static final String PROCESS_DEFINITIONS_URL = "/v1/process-definitions/";
    private static final String PROCESS_WITH_VARIABLES_2 = "ProcessWithVariables2";
    private static final String PROCESS_POOL_LANE = "process_pool1";

    @Test
    public void shouldRetrieveListOfProcessDefinition() {
        //given
        //processes are automatically deployed from src/test/resources/processes

        //when
        ResponseEntity<PagedResources<ProcessDefinition>> entity = getProcessDefinitions();

        //then
        assertThat(entity).isNotNull();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getContent()).extracting(ProcessDefinition::getName).contains(
                "ProcessWithVariables",
                "ProcessWithVariables2",
                "process_pool1",
                "SimpleProcess",
                "ProcessWithBoundarySignal");
    }

    private ProcessDefinition getProcessDefinition(String name) {
        ResponseEntity<PagedResources<ProcessDefinition>> processDefinitionsEntity = getProcessDefinitions();
        Iterator<ProcessDefinition> it = processDefinitionsEntity.getBody().getContent().iterator();
        ProcessDefinition aProcessDefinition;
        do {
            aProcessDefinition = it.next();
        } while (!aProcessDefinition.getName().equals(name));

        return aProcessDefinition;
    }

    private ResponseEntity<PagedResources<ProcessDefinition>> getProcessDefinitions() {
        ParameterizedTypeReference<PagedResources<ProcessDefinition>> responseType = new ParameterizedTypeReference<PagedResources<ProcessDefinition>>() {
        };
        return restTemplate.exchange(PROCESS_DEFINITIONS_URL,
                                     HttpMethod.GET,
                                     null,
                                     responseType);
    }

    @Test
    public void shouldReturnProcessDefinitionById() {
        //given
        ParameterizedTypeReference<ProcessDefinition> responseType = new ParameterizedTypeReference<ProcessDefinition>() {
        };

        ResponseEntity<PagedResources<ProcessDefinition>> processDefinitionsEntity = getProcessDefinitions();
        assertThat(processDefinitionsEntity).isNotNull();
        assertThat(processDefinitionsEntity.getBody()).isNotNull();
        assertThat(processDefinitionsEntity.getBody().getContent()).isNotEmpty();
        ProcessDefinition aProcessDefinition = processDefinitionsEntity.getBody().getContent().iterator().next();

        //when
        ResponseEntity<ProcessDefinition> entity = restTemplate.exchange(PROCESS_DEFINITIONS_URL + aProcessDefinition.getId(),
                                                                         HttpMethod.GET,
                                                                         null,
                                                                         responseType);

        //then
        assertThat(entity).isNotNull();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getId()).isEqualTo(aProcessDefinition.getId());
    }

    @Test
    public void shouldReturnProcessDefinitionMetadata() {
        //given
        ParameterizedTypeReference<ProcessDefinitionMeta> responseType = new ParameterizedTypeReference<ProcessDefinitionMeta>() {
        };

        ProcessDefinition aProcessDefinition = getProcessDefinition(PROCESS_WITH_VARIABLES_2);

        //when
        ResponseEntity<ProcessDefinitionMeta> entity = restTemplate.exchange(PROCESS_DEFINITIONS_URL + aProcessDefinition.getId() + "/meta",
                                                                             HttpMethod.GET,
                                                                             null,
                                                                             responseType);
        //then
        assertThat(entity).isNotNull();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getVariables()).hasSize(3);
        assertThat(entity.getBody().getUsers()).hasSize(4);
        assertThat(entity.getBody().getGroups()).hasSize(4);
        assertThat(entity.getBody().getUserTasks()).hasSize(2);
        assertThat(entity.getBody().getServiceTasks()).hasSize(2);
    }

    @Test
    public void shouldReturnProcessDefinitionMetadataForPoolLane() {
        //given
        ParameterizedTypeReference<ProcessDefinitionMeta> responseType = new ParameterizedTypeReference<ProcessDefinitionMeta>() {
        };

        ProcessDefinition aProcessDefinition = getProcessDefinition(PROCESS_POOL_LANE);

        //when
        ResponseEntity<ProcessDefinitionMeta> entity = restTemplate.exchange(PROCESS_DEFINITIONS_URL + aProcessDefinition.getId() + "/meta",
                                                                             HttpMethod.GET,
                                                                             null,
                                                                             responseType);
        //then
        assertThat(entity).isNotNull();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getVariables()).hasSize(6);
        assertThat(entity.getBody().getUsers()).hasSize(4);
        assertThat(entity.getBody().getGroups()).hasSize(4);
        assertThat(entity.getBody().getUserTasks()).hasSize(3);
        assertThat(entity.getBody().getServiceTasks()).hasSize(3);
    }

    @Test
    public void shouldRetriveProcessModel() throws Exception {

        ProcessDefinition aProcessDefinition = getProcessDefinition(PROCESS_POOL_LANE);

        //when
        String responseData = executeRequest(PROCESS_DEFINITIONS_URL + aProcessDefinition.getId() + "/model",
                                             HttpMethod.GET,
                                             "application/xml");

        //then
        assertThat(responseData).isNotNull();
        assertThat(responseData).isEqualTo(TestResourceUtil.getProcessXml(aProcessDefinition.getId().split(":")[0]));
    }

    @Test
    public void shouldRetriveBpmnModel() throws Exception {
        //given
        ProcessDefinition aProcessDefinition = getProcessDefinition(PROCESS_WITH_VARIABLES_2);

        //when
        String responseData = executeRequest(PROCESS_DEFINITIONS_URL + aProcessDefinition.getId() + "/model",
                                             HttpMethod.GET,
                                             "application/json");

        //then
        assertThat(responseData).isNotNull();

        BpmnModel targetModel = new BpmnJsonConverter().convertToBpmnModel(new ObjectMapper().readTree(responseData));
        final InputStream byteArrayInputStream = new ByteArrayInputStream(TestResourceUtil.getProcessXml(aProcessDefinition.getId()
                                                                                                                 .split(":")[0]).getBytes());
        BpmnModel sourceModel = new BpmnXMLConverter().convertToBpmnModel(() -> byteArrayInputStream,
                                                                          false,
                                                                          false);
        assertThat(targetModel.getMainProcess().getId().equals(sourceModel.getMainProcess().getId()));
        for (FlowElement element : targetModel.getMainProcess().getFlowElements()) {
            assertThat(sourceModel.getFlowElement(element.getId()) != null);
        }
    }

    @Test
    public void shouldRetrieveDiagram() throws Exception {

        ProcessDefinition aProcessDefinition = getProcessDefinition(PROCESS_POOL_LANE);

        //when
        String responseData = executeRequest(PROCESS_DEFINITIONS_URL + aProcessDefinition.getId() + "/model",
                                             HttpMethod.GET,
                                             "image/svg+xml");

        //then
        assertThat(responseData).isNotNull();
        final InputStream byteArrayInputStream = new ByteArrayInputStream(TestResourceUtil.getProcessXml(aProcessDefinition.getId()
                                                                                                                 .split(":")[0]).getBytes());
        BpmnModel sourceModel = new BpmnXMLConverter().convertToBpmnModel(() -> byteArrayInputStream,
                                                                          false,
                                                                          false);
        String activityFontName = processDiagramGenerator.getDefaultActivityFontName();
        String labelFontName = processDiagramGenerator.getDefaultLabelFontName();
        String annotationFontName = processDiagramGenerator.getDefaultAnnotationFontName();
        try (InputStream is = processDiagramGenerator.generateDiagram(sourceModel,
                                                                      activityFontName,
                                                                      labelFontName,
                                                                      annotationFontName)) {
            String sourceSvg = new String(IoUtil.readInputStream(is,
                                                                 null),
                                          "UTF-8");
            assertThat(responseData).isEqualTo(sourceSvg);
        }
    }

    private String executeRequest(String url,
                                  HttpMethod method,
                                  String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", contentType);
        ResponseEntity<String> response = restTemplate.exchange(url,
                                                                method,
                                                                new HttpEntity<>(headers),
                                                                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}