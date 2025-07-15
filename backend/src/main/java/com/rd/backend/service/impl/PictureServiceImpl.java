package com.rd.backend.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import com.rd.backend.manager.upload.FilePictureUpload;
import com.rd.backend.manager.upload.PictureUploadTemplate;
import com.rd.backend.manager.upload.UrlPictureUpload;
import com.rd.backend.mapper.PictureMapper;
import com.rd.backend.model.dto.file.UploadPictureResult;
import com.rd.backend.model.dto.picture.PictureQueryRequest;
import com.rd.backend.model.dto.picture.PictureReviewRequest;
import com.rd.backend.model.dto.picture.PictureUploadByBatchRequest;
import com.rd.backend.model.dto.picture.PictureUploadRequest;
import com.rd.backend.model.entity.Picture;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.enums.PictureReviewStatusEnum;
import com.rd.backend.model.vo.PictureVO;
import com.rd.backend.model.vo.UserVO;
import com.rd.backend.service.PictureService;
import com.rd.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 如果是更新，判断是否是自己的图片，仅本人和管理员可更新
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        // 上传图片
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        // 根据 inputSource 的类型，区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
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
                .eq(ObjUtil.isNotEmpty(req.getPicScale()), Picture::getPicScale, req.getPicScale())
                .eq(ObjUtil.isNotEmpty(req.getReviewStatus()), Picture::getReviewStatus, req.getReviewStatus())
                .like(ObjUtil.isNotEmpty(req.getReviewMessage()), Picture::getReviewMessage, req.getReviewMessage())
                .eq(ObjUtil.isNotEmpty(req.getReviewerId()), Picture::getReviewerId, req.getReviewerId());

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
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewPicture(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
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
}




