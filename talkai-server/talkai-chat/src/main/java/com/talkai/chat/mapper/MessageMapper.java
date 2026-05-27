package com.talkai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.talkai.chat.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
