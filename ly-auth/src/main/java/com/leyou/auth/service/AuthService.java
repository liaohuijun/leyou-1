package com.leyou.auth.service;

import com.leyou.auth.config.JwtProperties;
import com.leyou.common.auth.entity.Payload;
import com.leyou.common.auth.entity.UserInfo;
import com.leyou.common.auth.utils.JwtUtils;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exceptions.LyException;
import com.leyou.common.utils.CookieUtils;
import com.leyou.item.dto.UserDTO;
import com.leyou.user.client.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * @Author: 姜光明
 * @Date: 2019/5/17 8:58
 */
@Slf4j
@Service
public class AuthService {

    @Autowired
    private JwtProperties prop;
    @Autowired
    private UserClient userClient;

    private static final String USER_ROLE = "role_user";

    public void login(String username, String password, HttpServletResponse response) {

        try {
            //1.查询用户
            UserDTO userDTO = userClient.queryUserByUsernameAndPassword(username, password);
            //生成userInfo，没有写权限，所以暂时都用guest
            UserInfo userInfo = new UserInfo(userDTO.getId(),userDTO.getUsername(),USER_ROLE);
            //2 生成token
            String token = JwtUtils.generateTokenExpireInMinutes(userInfo, prop.getPrivateKey(), prop.getUser().getExpire());
            //3 写入cookie
            //3.1token有效期
            //3.2cookie名称
            //3.3 cookie的domain属性，决定cookie在哪些域名下生效
            CookieUtils.newCookieBuilder()
                    .response(response) // response,用于写cookie
                    .httpOnly(true) // 保证安全防止XSS攻击，不允许JS操作cookie
                    .domain(prop.getUser().getCookieDomain()) // 设置domain
                    .name(prop.getUser().getCookieName()).value(token) // 设置cookie名称和值
                    .build();// 写入cookie
        } catch (Exception e) {
            throw new LyException(ExceptionEnum.INVALID_USERNAME_PASSWORD);
        }


    }

    /**
     * 验证用户信息
     *
     * @param request
     * @param response
     * @return
     */
    public UserInfo verifyUser(HttpServletRequest request, HttpServletResponse response) {
        try {
            //读取cookie
            String token = CookieUtils.getCookieValue(request, prop.getUser().getCookieName());
            //获取token 信息
            Payload<UserInfo> payload = JwtUtils.getInfoFromToken(token, prop.getPublicKey(), UserInfo.class);

            //获取过期时间
            Date expiration = payload.getExpiration();
            //获取刷新时间
            DateTime refreshTime = new DateTime(expiration.getTime()).minusMinutes(prop.getUser().getMinRefreshInterval());
            //判断是否已经过了刷新时间
            if (refreshTime.isBefore(System.currentTimeMillis())) {
                //如果过了刷新时间，则生成新token
                token = JwtUtils.generateTokenExpireInMinutes(payload.getUserInfo(), prop.getPrivateKey(), prop.getUser().getExpire());
                //写入cookie
                CookieUtils.newCookieBuilder()
                        //response，用于写cookie
                        .response(response)
                        //保证安全防止XSS攻击，不允许JS操作cookie
                        .httpOnly(true)
                        //设置domain
                        .domain(prop.getUser().getCookieDomain())
                        //设置cookie的名称和值
                        .name(prop.getUser().getCookieName())
                        //写入cookie
                        .build();
            }


            return payload.getUserInfo();
        } catch (Exception e) {
            log.error("用户信息认证失败", e);
            // 抛出异常，证明token无效，直接返回401
            throw new LyException(ExceptionEnum.UNAUTHORIZED);
        }
    }
}