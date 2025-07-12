package com.rd.backend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rd.backend.constant.UserConstant;
import com.rd.backend.exception.BusinessException;
import com.rd.backend.exception.ErrorCode;
import com.rd.backend.exception.ThrowUtils;
import com.rd.backend.mapper.UserMapper;
import com.rd.backend.model.dto.user.UserQueryRequest;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.enums.UserRoleEnum;
import com.rd.backend.model.vo.LoginUserVO;
import com.rd.backend.model.vo.UserVO;
import com.rd.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author RdLante
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-07-07 20:27:44
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 校验参数
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号过短");
        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8,
                ErrorCode.PARAMS_ERROR, "用户密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "两次密码不一致");

        // 检查用户账号在数据库中是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        Long count = this.baseMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "用户账号已存在");

        // 密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "用户注册失败，数据库错误");

        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号错误");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "用户密码错误");
        // 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 查询是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        queryWrapper.eq("user_password", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者账号或密码错误");
        }
        // 保存用户登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);

        return this.getLoginUserVO(user);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    @Override
    public String getEncryptPassword(String userPassword) {

        // 加盐，混淆密码
        final String SALT = "rdlante";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public User getLoginUer(HttpServletRequest request) {
        // 是否登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        Long userId = currentUser.getId();
        currentUser = this.getById(userId);
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        return currentUser;
    }

    /**
     * 获取脱敏后的登录用户信息
     * @param user
     * @return
     */
    @Override
    public LoginUserVO getLoginUserVO(User user){
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public LambdaQueryWrapper<User> getQueryWrapper(UserQueryRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        // 基本过滤
        queryWrapper.eq(ObjUtil.isNotNull(req.getId()), User::getId, req.getId())
                .eq(StrUtil.isNotBlank(req.getUserRole()), User::getUserRole, req.getUserRole())
                .like(StrUtil.isNotBlank(req.getUserAccount()), User::getUserAccount, req.getUserAccount())
                .like(StrUtil.isNotBlank(req.getUserName()), User::getUserName, req.getUserName())
                .like(StrUtil.isNotBlank(req.getUserProfile()), User::getUserProfile, req.getUserProfile());

        // 排序逻辑
        String sortField = req.getSortField();
        String sortOrder = req.getSortOrder();
        boolean asc = "ascend".equals(sortOrder);
        if (StrUtil.isNotBlank(sortField)) {
            switch (sortField) {
                case "createTime":
                    queryWrapper.orderBy(true, asc, User::getCreateTime);
                    break;
                case "updateTime":
                    queryWrapper.orderBy(true, asc, User::getUpdateTime);
                    break;
                case "editTime":
                    queryWrapper.orderBy(true, asc, User::getEditTime);
                    break;
                case "userName":
                    queryWrapper.orderBy(true, asc, User::getUserName);
                    break;
                // 如果还有别的可排序字段，按需再加 case
                default:
                    // 不支持的字段可以忽略或抛异常
                    break;
            }
        }

        return queryWrapper;
    }


}




