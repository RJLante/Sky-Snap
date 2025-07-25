package com.rd.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.rd.backend.model.dto.space.SpaceAddRequest;
import com.rd.backend.model.dto.space.SpaceQueryRequest;
import com.rd.backend.model.entity.Space;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author RJLante
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-07-17 19:38:40
*/
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 获取查询对象
     * @param spaceQueryRequest
     * @return
     */
    LambdaQueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取空间封装
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 分页获取空间封装
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 校验空间
     * @param space
     * @param add 是否为创建时校验
     */
    void validSpace(Space space, boolean add);

    /**
     * 根据空间级别填充空间对象
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间权限
     * @param loginUser
     * @param space
     */
    void checkSpaceAuth(User loginUser, Space space);

}
