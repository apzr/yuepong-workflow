package com.yuepong.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuepong.workflow.dto.BpmnByte;
import com.yuepong.workflow.dto.SysFlowExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BpmnByteMapper
 *
 * @author apr
 * @date 2021/11/12 10:47
 */
@Mapper
@Component
public interface BpmnByteMapper extends BaseMapper<BpmnByte> {
}
