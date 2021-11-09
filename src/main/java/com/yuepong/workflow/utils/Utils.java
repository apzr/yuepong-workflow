package com.yuepong.workflow.utils;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuepong.jdev.exception.BizException;
import org.activiti.api.runtime.model.impl.ProcessDefinitionImpl;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.impl.cmd.NeedsActiveTaskCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManagerImpl;
import org.activiti.engine.repository.Model;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Utils
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/26 09:22:51
 **/
public class Utils {

    public static void initModel(Model m, String jsonXml, ObjectMapper objectMapper){
        if(Objects.isNull(m))
            throw new BizException("没有找到模型");

        JSONObject jsonObject = JSONObject.parseObject(jsonXml);

        JSONObject properties = jsonObject.getJSONObject("properties");
        String modelName = properties.getString("name");
        String modelKey = properties.getString("process_id");
        String modelCategory = properties.getString("processCategory");
        String modelDescription = properties.getString("documentation");

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME,modelName);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, modelDescription);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, 1);

        m.setName(modelName);
        m.setKey(modelKey);
        m.setCategory(modelCategory);
        m.setMetaInfo(modelNode.toString());
    }
}
