package net.xdclass.service;

import net.xdclass.request.UserLoginRequest;
import net.xdclass.request.UserRegisterRequest;
import net.xdclass.util.JsonData;
import net.xdclass.vo.UserVO;

public interface UserService {

    /**
     * 用户注册
     * @param registerRequest
     * @return
     */
    JsonData register(UserRegisterRequest registerRequest);

    /**
     * 用户登录
     * @param userLoginRequest
     * @return
     */
    JsonData login(UserLoginRequest userLoginRequest);

    /**
     * 查询用户详情
     * @return
     */
    UserVO findUserDetail();
}
