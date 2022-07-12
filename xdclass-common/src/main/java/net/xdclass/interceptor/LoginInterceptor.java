package net.xdclass.interceptor;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.model.LoginUser;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JWTUtil;
import net.xdclass.util.JsonData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    public static ThreadLocal<LoginUser> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 获取token：在请求头或请求参数中
        String accessToken = request.getHeader("token");
        if(accessToken == null) {
            accessToken = request.getParameter("token");
        }

        // token不为空
        if(StringUtils.isNotBlank(accessToken)){
            // 解密并校验token
            // claims为获取的token中包含的用户信息
            Claims claims = JWTUtil.checkJWT(accessToken);
            // 未通过校验
            if(claims == null){
                // 账号未登录
                CommonUtil.sendJsonMessage(response, JsonData.buildResult(BizCodeEnum.ACCOUNT_UNLOGIN));
                return false;
            }

            // 账号已登录
            // 获取用户信息：ID、头像、名字、邮箱
            long userId = Long.parseLong(claims.get("id").toString());
            String headImg = (String)claims.get("head_img");
            String name = (String)claims.get("name");
            String mail = (String)claims.get("mail");

            // 根据token中的用户信息，创建一个已登录用户对象
            // 建造者模式
            LoginUser loginUser = LoginUser
                    .builder()
                    .headImg(headImg)
                    .name(name)
                    .id(userId)
                    .mail(mail).build();
//            loginUser.setName(name);
//            loginUser.setHeadImg(headImg);
//            loginUser.setId(userId);
//            loginUser.setMail(mail);

            //【之前】：通过attribute传递用户信息
            //request.setAttribute("loginUser",loginUser);

            //【现在】：在拦截器中通过threadLocal传递用户登录信息
            // 后续的 controller、service、mapper 可以通过ThreadLocal直接获取到用户信息，避免了传参，类似于全局变量的概念
            threadLocal.set(loginUser);

            return true;
        }

        // token为空，账号未登录
        CommonUtil.sendJsonMessage(response,JsonData.buildResult(BizCodeEnum.ACCOUNT_UNLOGIN));
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        threadLocal.remove();
    }
}
