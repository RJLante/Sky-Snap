package com.rd.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rd.backend.exception.BusinessException;
import com.rd.backend.exception.ErrorCode;
import com.rd.backend.exception.ThrowUtils;
import com.rd.backend.manager.FileManager;
import com.rd.backend.mapper.PictureMapper;
import com.rd.backend.model.dto.file.UploadPictureResult;
import com.rd.backend.model.dto.picture.PictureQueryRequest;
import com.rd.backend.model.dto.picture.PictureUploadRequest;
import com.rd.backend.model.entity.Picture;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.vo.PictureVO;
import com.rd.backend.model.vo.UserVO;
import com.rd.backend.service.PictureService;
import com.rd.backend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author RJLante
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-07-12 15:44:20
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        // 上传图片
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则为新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "数据库操作失败");
        return PictureVO.objToVo(picture);
    }


    @Override
    public LambdaQueryWrapper<Picture> getQueryWrapper(PictureQueryRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        LambdaQueryWrapper<Picture> query = new LambdaQueryWrapper<>();

        // 多字段搜索
        String searchText = req.getSearchText();
        if (StrUtil.isNotBlank(searchText)) {
            query.and(wrapper ->
                    wrapper.like(Picture::getName, searchText)
                            .or()
                            .like(Picture::getIntroduction, searchText)
            );
        }

        // 基本过滤
        query.eq(ObjUtil.isNotEmpty(req.getId()), Picture::getId, req.getId())
                .eq(ObjUtil.isNotEmpty(req.getUserId()), Picture::getUserId, req.getUserId())
                .like(StrUtil.isNotBlank(req.getName()), Picture::getName, req.getName())
                .like(StrUtil.isNotBlank(req.getIntroduction()), Picture::getIntroduction, req.getIntroduction())
                .eq(StrUtil.isNotBlank(req.getCategory()), Picture::getCategory, req.getCategory())
                .like(StrUtil.isNotBlank(req.getPicFormat()), Picture::getPicFormat, req.getPicFormat())
                .eq(ObjUtil.isNotEmpty(req.getPicWidth()), Picture::getPicWidth, req.getPicWidth())
                .eq(ObjUtil.isNotEmpty(req.getPicHeight()), Picture::getPicHeight, req.getPicHeight())
                .eq(ObjUtil.isNotEmpty(req.getPicSize()), Picture::getPicSize, req.getPicSize())
                .eq(ObjUtil.isNotEmpty(req.getPicScale()), Picture::getPicScale, req.getPicScale());

        // JSON 数组中的标签查询
        List<String> tags = req.getTags();
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                query.like(Picture::getTags, "\"" + tag + "\"");
            }
        }

        // 排序
        String sortField = req.getSortField();
        String sortOrder = req.getSortOrder();
        boolean asc = "ascend".equals(sortOrder);
        if (StrUtil.isNotBlank(sortField)) {
            switch (sortField) {
                case "id":
                    query.orderBy(true, asc, Picture::getId);
                    break;
                case "userId":
                    query.orderBy(true, asc, Picture::getUserId);
                    break;
                case "name":
                    query.orderBy(true, asc, Picture::getName);
                    break;
                case "picWidth":
                    query.orderBy(true, asc, Picture::getPicWidth);
                    break;
                case "picHeight":
                    query.orderBy(true, asc, Picture::getPicHeight);
                    break;
                case "picSize":
                    query.orderBy(true, asc, Picture::getPicSize);
                    break;
                case "picScale":
                    query.orderBy(true, asc, Picture::getPicScale);
                    break;
                case "createTime":
                    query.orderBy(true, asc, Picture::getCreateTime);
                    break;
                case "updateTime":
                    query.orderBy(true, asc, Picture::getUpdateTime);
                    break;
                default:
                    // 不支持的排序字段，忽略
                    break;
            }
        }
        return query;
    }


    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }


    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


    /**
     * 校验图片
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

}




