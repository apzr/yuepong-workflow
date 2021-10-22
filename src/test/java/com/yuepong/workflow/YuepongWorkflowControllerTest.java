package com.yuepong.workflow;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * @author : Alex Hu
 * date : 2020/3/24 下午14:05
 * description : Controller测试，关键技术：WithMockUser模拟授权+MockMvc
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
        //,classes = {DeployController.class}
)
@AutoConfigureMockMvc
@Slf4j
public class YuepongWorkflowControllerTest {
    @Autowired
    protected MockMvc mockMvc;

    @Test
    public void t1() {
        log.info("yuepongWorkflowApplicationTest.t1");
    }

    @Test
    @WithMockUser(username = "admin")
    public void testDeploy() throws Exception {
        String deployID;
        //deployID = "29a3bd58-6de0-11ea-a8fc-acde48001122";
        //testDeleteDeploy(deployID);

        //部署bpmn文件的流程
        //deployID = testDeployBPMN("leaveProcess");

        //删除本流程
        //testDeleteDeploy(deployID);

        //部署zip文件的流程
        //deployID = testDeployZIP("leaveProcess.zip");

        //启动一个流程
        startTest("测试流程", "myProcess_1");

        //删除本流程
        //testDeleteDeploy(deployID);
    }

    /**
     * 删除部署
     */
    private void testDeleteDeploy(String deployID) throws Exception {
        MvcResult result;

        result = mockMvc.perform(
                MockMvcRequestBuilders.post("/deleteProcess")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .param("deploymentId", deployID)
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        log.info("result = " + result.getResponse().getContentAsString());
    }

    /**
     * 部署zip文件
     *
     * @return
     */
    public String testDeployZIP(String filename) throws Exception {
        MvcResult result;
        String deployID;

        result = mockMvc.perform(
                MockMvcRequestBuilders.post("/deployZIP")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .param("zipName", filename)
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        log.info("result = " + result.getResponse().getContentAsString());

        deployID = JSONObject.parseObject(result.getResponse().getContentAsString()).getJSONObject("data").getString("deployID");
        return deployID;
    }

    /**
     * 部署普通文件
     * 需要有bpmn png 两个文件
     */
    private String testDeployBPMN(String filename) throws Exception {
        MvcResult result;
        String deployID;

        result = mockMvc.perform(
                MockMvcRequestBuilders.post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .param("bpmnName", filename)
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        log.info("result = " + result.getResponse().getContentAsString());

        deployID = JSONObject.parseObject(result.getResponse().getContentAsString()).getJSONObject("data").getString("deployID");
        return deployID;
    }

    /**
     * 新建一个流程
     */
    public void startTest(String userKey, String processKey) throws Exception {
        MvcResult result;

        result = mockMvc.perform(
                MockMvcRequestBuilders.post("/start")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .param("user", userKey)
                        .param("processKey", processKey)
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        log.info("result = " + result.getResponse().getContentAsString());
    }
}