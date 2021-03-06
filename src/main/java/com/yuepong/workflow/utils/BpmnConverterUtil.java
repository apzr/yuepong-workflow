package com.yuepong.workflow.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuepong.workflow.utils.converter.BpmnJsonConverter;
import com.yuepong.workflow.utils.converter.CallActivityXMLConverter;
import com.yuepong.workflow.utils.converter.CustomBpmnJsonConverter;
import com.yuepong.workflow.utils.converter.UserTaskXMLConverter;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * BpmnConverterUtil
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/25 14:06:19
 **/
public class BpmnConverterUtil {
    /**
     * 描述 : 将JsonNode格式的流程图转为前端适应的xml
     *
     * @param jsonStr
     * @return java.lang.String
     * @author yiyoung
     * @date 2020/02/27
     */
    public static String converterJsonToWebXml(String jsonStr) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(jsonStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        CustomBpmnJsonConverter customBpmnJsonConverter = new CustomBpmnJsonConverter();
        BpmnModel bpmnModel = customBpmnJsonConverter.convertToBpmnModel(jsonNode);
        // 如果没有Processes,认为是一个空流程
        if (bpmnModel.getProcesses().isEmpty()) {
            return "";
        }
        BpmnXMLConverter bpmnXMLConverter = new BpmnXMLConverter();
        byte[] bytes = bpmnXMLConverter.convertToXML(bpmnModel);
        return new String(bytes);
    }

    /**
     * 描述 : 将JsonNode格式的流程图转为标准的xml
     *
     * @param jsonStr
     * @return java.lang.String
     * @author jx
     * @date 2019/10/24 17:23
     */
    public static String converterJsonToXml(String jsonStr) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(jsonStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BpmnJsonConverter bpmnJsonConverter = new BpmnJsonConverter();
        BpmnModel bpmnModel = bpmnJsonConverter.convertToBpmnModel(jsonNode);
        // 如果没有Processes,认为是一个空流程
        if (bpmnModel.getProcesses().isEmpty()) {
            return "";
        }
        BpmnXMLConverter bpmnXMLConverter = new BpmnXMLConverter();
        byte[] bytes = bpmnXMLConverter.convertToXML(bpmnModel);
        return new String(bytes);
    }

    /**
     * 描述 : 将xml转为jsonnode
     *
     * @param xml
     * @return com.fasterxml.jackson.databind.JsonNode
     * @author jx
     * @date 2019/10/24 17:31
     */
    public static JsonNode converterXmlToJson(String xml) {
        // 创建转换对象
        BpmnXMLConverter bpmnXMLConverter = new BpmnXMLConverter();
        // XMLStreamReader读取XML资源
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        StringReader stringReader = new StringReader(xml);
        XMLStreamReader xmlStreamReader = null;
        try {
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(stringReader);
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
        BpmnXMLConverter.addConverter(new UserTaskXMLConverter());
        BpmnXMLConverter.addConverter(new CallActivityXMLConverter());
        // 把xml转换成BpmnModel对象
        BpmnModel bpmnModel = bpmnXMLConverter.convertToBpmnModel(xmlStreamReader);
        // 创建转换对象
        BpmnJsonConverter bpmnJsonConverter = new BpmnJsonConverter();
        // 把BpmnModel对象转换成json
        JsonNode jsonNodes = bpmnJsonConverter.convertToJson(bpmnModel);
        return jsonNodes;
    }
}
