package com.yuepong.workflow;

 import lombok.extern.slf4j.Slf4j;
 import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
 import org.activiti.engine.repository.Deployment;
 import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
 import java.util.Map;

/**
 * @author : Alex Hu
 * date : 2020/3/24 下午14:05
 * description : 引擎测试，关键技术：WithMockUser模拟授权
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
class YuepongWorkflowApplicationTest {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    @Test
    @WithMockUser("admin")
    public void deployTest() {
        //创建一个部署对象
        Deployment deployment = repositoryService.createDeployment()
                .name("请假流程")
                .addClasspathResource("processes/leaveProcess.bpmn")
                .addClasspathResource("processes/leaveProcess.png")
                .deploy();
        log.info("部署ID：" + deployment.getId());
        log.info("部署名称：" + deployment.getName());
    }

    /**
     * 启动流程实例分配任务给个人
     */
    @Test
    @WithMockUser("admin")
    public void startTest() {

        String userKey = "PTM";//脑补一下这个是从前台传过来的数据
        String processDefinitionKey = "myProcess_1";//每一个流程有对应的一个key这个是某一个流程内固定的写在bpmn内的
        HashMap<String, Object> variables = new HashMap<>();
        variables.put("userKey", userKey);//userKey在上文的流程变量中指定了

        ProcessInstance instance = runtimeService
                .startProcessInstanceByKey(processDefinitionKey, variables);

        log.info("流程实例ID:" + instance.getId());
        log.info("流程定义ID:" + instance.getProcessDefinitionId());
    }

    /**
     * 查询流程实例
     */
    @Test
    @WithMockUser("admin")
    public void searchProcessInstanceTest() {
        String processDefinitionKey = "oneTaskProcess";
        String processDefinitionId = "8bc37e4b-6e38-11ea-a516-acde48001122";
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                //.processDefinitionKey(processDefinitionKey)
                .processInstanceId(processDefinitionId)
                .singleResult();
        log.info("流程实例ID:" + pi.getId());
        log.info("流程定义ID:" + pi.getProcessDefinitionId());
        //验证是否启动成功
        //通过查询正在运行的流程实例来判断

        //ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
        ////根据流程实例ID来查询
        //List<ProcessInstance> runningList = processInstanceQuery.processInstanceId(processDefinitionId).list();
        //log.info("根据流程ID查询条数:{}"+runningList.size());
    }

    /**
     * 查询当前人的个人任务
     */
    @Test
    @WithMockUser("admin")
    public void findTaskTest() {
        String assignee = "PTM";
        List<Task> list = taskService.createTaskQuery()//创建任务查询对象
                .taskAssignee(assignee)//指定个人任务查询
                .list();
        if (list != null && list.size() > 0) {
            for (Task task : list) {
                log.info("任务ID:" + task.getId());
                log.info("任务名称:" + task.getName());
                log.info("任务的创建时间:" + task.getCreateTime());
                log.info("任务的办理人:" + task.getAssignee());
                log.info("流程实例ID：" + task.getProcessInstanceId());
                log.info("执行对象ID:" + task.getExecutionId());
                log.info("流程定义ID:" + task.getProcessDefinitionId());
            }

        } else {
            log.info("没有任务");
        }
    }
    /**查询当前人的个人任务*/
    @WithMockUser("admin")
    @Test
    public void findTaskTest1(){
        String assignee = "a";
        List<Task> list = taskService.createTaskQuery()//创建任务查询对象
                .taskAssignee(assignee)//指定个人任务查询
                .list();
        if(list!=null && list.size()>0){
            for(Task task:list){
                log.info("任务ID:"+task.getId());
                log.info("任务名称:"+task.getName());
                log.info("任务的创建时间:"+task.getCreateTime());
                log.info("任务的办理人:"+task.getAssignee());
                log.info("流程实例ID："+task.getProcessInstanceId());
                log.info("执行对象ID:"+task.getExecutionId());
                log.info("流程定义ID:"+task.getProcessDefinitionId());
                log.info("getOwner:"+task.getOwner());
                log.info("getCategory:"+task.getCategory());
                log.info("getDescription:"+task.getDescription());
                log.info("getFormKey:"+task.getFormKey());
                Map<String, Object> map = task.getProcessVariables();
                for (Map.Entry<String, Object> m : map.entrySet()) {
                    log.info("key:" + m.getKey() + " value:" + m.getValue());
                }
                for (Map.Entry<String, Object> m : task.getTaskLocalVariables().entrySet()) {
                    log.info("key:" + m.getKey() + " value:" + m.getValue());
                }

            }
        }
    }

    @Test
    @WithMockUser("admin")
    public void completeTaskTask(){
        //任务ID
        String taskId = "6825fdf2-7e04-11e9-a0c6-408d5ccf513c";

        HashMap<String, Object> variables=new HashMap<>();
        variables.put("days", 1);//userKey在上文的流程变量中指定了

        taskService.complete(taskId,variables);
        log.info("完成任务：任务ID："+taskId);
    }

    /**查询当前人的组任务*/
    @Test
    @WithMockUser("admin")
    public void findTaskGroupTest(){

        String assignee = "a";
        List<Task> list = taskService.createTaskQuery()//创建任务查询对象
//                .taskCandidateUser("ZJ")//指定组任务查询
                .taskAssignee(assignee)
                .list();
        String taskid ="";
        String instanceId ="";
        if(list!=null && list.size()>0){
            for(Task task:list){
                log.info("任务ID:"+task.getId());
                log.info("任务名称:"+task.getName());
                log.info("任务的创建时间:"+task.getCreateTime());
                log.info("任务的办理人:"+task.getAssignee());
                log.info("流程实例ID："+task.getProcessInstanceId());
                log.info("执行对象ID:"+task.getExecutionId());
                log.info("流程定义ID:"+task.getProcessDefinitionId());
            }
        }
    }

}
