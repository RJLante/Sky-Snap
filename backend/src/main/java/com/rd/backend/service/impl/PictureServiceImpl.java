package com.rd.backend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rd.backend.api.aliyunai.AliYunAiApi;
import com.rd.backend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.rd.backend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.rd.backend.exception.BusinessException;
import com.rd.backend.exception.ErrorCode;
import com.rd.backend.exception.ThrowUtils;
import com.rd.backend.manager.CosManager;
import com.rd.backend.manager.FileManager;
import com.rd.backend.manager.auth.SpaceUserAuthManager;
import com.rd.backend.manager.auth.model.SpaceUserPermissionConstant;
import com.rd.backend.manager.upload.FilePictureUpload;
import com.rd.backend.manager.upload.PictureUploadTemplate;
import com.rd.backend.manager.upload.UrlPictureUpload;
import com.rd.backend.mapper.PictureMapper;
import com.rd.backend.model.dto.file.UploadPictureResult;
import com.rd.backend.model.dto.picture.*;
import com.rd.backend.model.entity.Picture;
import com.rd.backend.model.entity.Space;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.enums.PictureReviewStatusEnum;
import com.rd.backend.model.enums.SpaceTypeEnum;
import com.rd.backend.model.vo.PictureVO;
import com.rd.backend.model.vo.UserVO;
import com.rd.backend.service.PictureService;
import com.rd.backend.service.SpaceService;
import com.rd.backend.service.UserService;
import com.rd.backend.untils.ColorSimilarUtils;
import com.rd.backend.untils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author RJLante
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-07-12 15:44:20
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 改为使用统一的权限校验
//            // 校验是否有空间的权限，仅管理员才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没用空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间大小不足");
            }
        }

        // 判断新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 改为使用统一的权限校验
//            // 如果是更新，判断是否是自己的图片，仅本人和管理员可更新
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有的图片的 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片空间 id 不一致");
                }
            }
        }
        // 上传图片
        // 按照用户 id 划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 根据 inputSource 的类型，区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 指定空间 id
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
//        picture.setPicColor(uploadPictureResult.getPicColor());
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        this.fillReviewPicture(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则为新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("total_size = total_size + " + picture.getPicSize())
                        .setSql("total_count = total_count + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            // 再次更新审核信息，避免被默认值覆盖
            if (Objects.equals(picture.getReviewStatus(), PictureReviewStatusEnum.PASS.getValue())) {
                Picture reviewUpdate = new Picture();
                reviewUpdate.setId(picture.getId());
                reviewUpdate.setReviewStatus(picture.getReviewStatus());
                reviewUpdate.setReviewerId(picture.getReviewerId());
                reviewUpdate.setReviewMessage(picture.getReviewMessage());
                reviewUpdate.setReviewTime(picture.getReviewTime());
                boolean reviewResult = this.updateById(reviewUpdate);
                ThrowUtils.throwIf(!reviewResult, ErrorCode.OPERATION_ERROR, "审核信息更新失败");
            }
            return picture;
        });
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
                .eq(ObjUtil.isNotEmpty(req.getPicScale()), Picture::getPicScale, req.getPicScale())
                .eq(ObjUtil.isNotEmpty(req.getReviewStatus()), Picture::getReviewStatus, req.getReviewStatus())
                .like(ObjUtil.isNotEmpty(req.getReviewMessage()), Picture::getReviewMessage, req.getReviewMessage())
                .eq(ObjUtil.isNotEmpty(req.getReviewerId()), Picture::getReviewerId, req.getReviewerId())
                .eq(ObjUtil.isNotEmpty(req.getSpaceId()), Picture::getSpaceId, req.getSpaceId())
                .isNull(req.isNullSpaceId(), Picture::getSpaceId)
                .ge(ObjUtil.isNotEmpty(req.getStartEditTime()), Picture::getEditTime, req.getStartEditTime())
                .lt(ObjUtil.isNotEmpty(req.getEndEditTime()), Picture::getEditTime, req.getEndEditTime());

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
                case "reviewTime":
                    query.orderBy(true, asc, Picture::getReviewTime);
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
     *
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

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验审核状态是否重复，已经是改状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片审核状态不能重复");
        }
        // 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewPicture(Picture picture, User loginUser) {
        // 默认待审核
        picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
            return;
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
//            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            Long spaceId = picture.getSpaceId();
            if (spaceId != null) {
                Space space = spaceService.getById(spaceId);
                if (space != null) {
                    // 私有空间上传，空间拥有者默认自动过审
                    if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()
                            && ObjUtil.equals(space.getUserId(), loginUser.getId())) {
                        picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
                        picture.setReviewerId(loginUser.getId());
                        picture.setReviewMessage("个人空间自动过审");
                        picture.setReviewTime(new Date());
                        return;
                    }

                    // 团队空间，根据成员权限判断是否自动过审
                    if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                        List<String> permissions = spaceUserAuthManager.getPermissionList(space, loginUser);
                        if (permissions.contains(SpaceUserPermissionConstant.PICTURE_UPLOAD)
                                || permissions.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
                            picture.setReviewerId(loginUser.getId());
                            picture.setReviewMessage("成员自动过审");
                            picture.setReviewTime(new Date());
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        // 名称前缀默认用搜索关键字
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        // checkPictureAuth(loginUser, oldPicture);

        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度（公共图库没有空间 id，无需更新）
            Long oldSpaceId = oldPicture.getSpaceId();
            if (oldSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldSpaceId)
                        .setSql("total_size = total_size - " + oldPicture.getPicSize())
                        .setSql("total_count = total_count - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });

        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        // checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewPicture(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下的所有图片（图片要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片直接返回空列表
        if (CollectionUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 将颜色字符串转为主色调
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 取前 12 张
                .collect(Collectors.toList());
        // 5. 返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollectionUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR, "图片 id 列表不能为空");
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList == null) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollectionUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRole = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRole(pictureList, nameRole);
        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "批量编辑图片失败");
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 权限校验
        // checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    /**
     * nameRole 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRole
     */
    private void fillPictureWithNameRole(List<Picture> pictureList, String nameRole) {
        if (StrUtil.isBlank(nameRole) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRole.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }
}




