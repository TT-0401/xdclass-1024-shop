package net.xdclass.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.request.UserLoginRequest;
import net.xdclass.request.UserRegisterRequest;
import net.xdclass.service.FileService;
import net.xdclass.service.UserService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author TT
 * @since 2022-05-23
 */
@Api(tags = "用户模块")
@RestController
@RequestMapping("/api/user/v1")
public class UserController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserService userService;

    /**
     * 上传用户头像
     *  默认最大是1M，超过则报错
     * @param file 文件
     * @return
     */
    // @RequestPart注解 接收文件以及其他更为复杂的数据类型
    @ApiOperation("用户头像上传")
    @PostMapping(value = "upload")
    public JsonData uploadUserImg(
            @ApiParam(value = "文件上传",required = true)
            @RequestPart("file") MultipartFile file){

        String result = fileService.uploadUserImg(file);

        return result != null ? JsonData.buildSuccess(result) : JsonData.buildResult(BizCodeEnum.FILE_UPLOAD_USER_IMG_FAIL);
    }

    /**
     * 用户注册
     * @param registerRequest
     * @return
     */
    @ApiOperation("用户注册")
    @PostMapping("register")
    public JsonData register(@ApiParam("用户注册请求对象") @RequestBody UserRegisterRequest registerRequest){
        JsonData jsonData = userService.register(registerRequest);
        return jsonData;
    }


    /**
     * 用户登录
     * @return
     */
    @ApiOperation("用户登录")
    @PostMapping("login")
    public JsonData login(@ApiParam("用户登录请求对象") @RequestBody UserLoginRequest userLoginRequest){
        JsonData jsonData = userService.login(userLoginRequest);
        return jsonData;
    }


//    刷新token的方案
//    @PostMapping("refresh_token")
//    public JsonData getRefreshToken(Map<String,Object> param){
//
//        //先去redis,找refresh_token是否存在
//        //refresh_token存在，解密accessToken
//        //重新调用JWTUtil.geneJsonWebToken() 生成accessToken
//        //重新生成refresh_token，并存储redis，设置30天过期时间
//        //返回给前端
//        return null;
//    }


    /**
     * 用户个人信息查询
     * @return
     */
    @ApiOperation("用户个人信息查询")
    @GetMapping("detail")
    public JsonData detail(){

        UserVO userVO = userService.findUserDetail();

        return JsonData.buildSuccess(userVO);
    }
}

