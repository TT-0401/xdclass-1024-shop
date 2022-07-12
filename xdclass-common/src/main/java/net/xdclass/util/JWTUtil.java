package net.xdclass.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.model.LoginUser;

import java.util.Date;

@Slf4j
public class JWTUtil {

    /**
     * token 过期时间
     */
    private static final long EXPIRE = 1000 * 60 * 60 * 24 * 7 * 10;

    /**
     * 加密 token 的秘钥
     */
    private static final String SECRET = "xdclass.net666";

    /**
     * token 前缀
     */
    private static final String TOKEN_PREFIX = "xdclass1024shop";

    /**
     * token 的 subject
     */
    private static final String SUBJECT = "xdclass";


    /**
     * 根据登录用户信息，生成token：头部 + 负载 + 签名
     *  token是经过 base64编码 生成的，所以可以解码，因此token不应该包含敏感信息
     * @param loginUser
     * @return
     */
    public static String geneJsonWebToken(LoginUser loginUser) {

        if (loginUser == null) {
            throw new NullPointerException("loginUser对象为空");
        }

                                      // 头部
        String token = Jwts.builder().setSubject(SUBJECT)
                // 负载
                .claim("head_img", loginUser.getHeadImg())
                .claim("id", loginUser.getId())
                .claim("name", loginUser.getName())
                .claim("mail", loginUser.getMail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                // 签名
                .signWith(SignatureAlgorithm.HS256, SECRET).compact();

        token = TOKEN_PREFIX + token;
        return token;
    }


    /**
     * 校验token的方法
     * @param token
     * @return
     */
    public static Claims checkJWT(String token) {

        try {

            final Claims claims = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token.replace(TOKEN_PREFIX, "")).getBody();

            return claims;

        } catch (Exception e) {
            log.info("jwt token 解密失败");
            return null;
        }
    }

}
