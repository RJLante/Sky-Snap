package com.rd.backend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.rd.backend.manager.auth.SpaceUserAuthManager;
import com.rd.backend.manager.auth.model.SpaceUserPermissionConstant;
import com.rd.backend.model.entity.Picture;
import com.rd.backend.model.entity.Space;
import com.rd.backend.model.entity.User;
import com.rd.backend.model.enums.SpaceTypeEnum;
import com.rd.backend.service.PictureService;
import com.rd.backend.service.SpaceService;
import com.rd.backend.service.UserService;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket握手拦截器，建立连接前要校验
 */
@Component
@Slf4j
public class WsHandShakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 从请求中获取参数
            String pictureId = httpServletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片信息，拒绝握手");
                return false;
            }
            // 获取当前登录用户
            User loginUer = userService.getLoginUer(httpServletRequest);
            if (ObjUtil.isEmpty(loginUer)) {
                log.error("用户未登录，拒绝握手");
                return false;
            }
            // 校验当前登录用户是否有当前图片的权限
            Picture picture = pictureService.getById(pictureId);
            if (ObjUtil.isEmpty(picture)) {
                log.error("图片不存在，拒绝握手");
                return false;
            }
            // 如果是团队空间，并且有编辑者权限，才能建立连接
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (ObjUtil.isEmpty(space)) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.error("图片所在空间非团队空间，拒绝握手");
                    return false;
                }
            }
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUer);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("用户没有编辑图片的权限，拒绝握手");
                return false;
            }
            // 设置用户登录信息等属性到 WebSocket 会话中
            attributes.put("user", loginUer);
            attributes.put("userId", loginUer.getId());
            attributes.put("pictureId", Long.valueOf(pictureId)); // 转换为 Long 类型
        }
        return true;
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Exception exception) {
    }
}
