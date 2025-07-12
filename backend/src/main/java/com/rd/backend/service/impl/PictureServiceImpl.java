package com.rd.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rd.backend.model.entity.Picture;
import com.rd.backend.service.PictureService;
import com.rd.backend.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
* @author RJLante
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-07-12 15:44:20
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

}




