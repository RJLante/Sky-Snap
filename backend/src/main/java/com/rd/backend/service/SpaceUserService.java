package com.rd.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rd.backend.model.dto.space.SpaceAddRequest;
import com.rd.backend.model.dto.space.SpaceQueryRequest;
import com.rd.backend.model.dto.spaceuser.SpaceUserAddRequest;
import com.rd.backend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.rd.backend.model.entity.Space;
import com.rd.backend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.vo.SpaceUserVO;
import com.rd.backend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author RJLante
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-07-25 12:49:52
*/
public interface SpaceUserService extends IService<SpaceUser> {
    /**
     * 创建空间成员
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 获取查询对象
     * @param spaceUserQueryRequest
     * @return
     */
    LambdaQueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 获取空间成员封装
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员封装（列表）
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 校验空间成员
     * @param spaceUser
     * @param add 是否为创建时校验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

}
