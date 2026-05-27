package com.talkai.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.talkai.photo.entity.Photo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {
}
