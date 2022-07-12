package net.xdclass.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.xdclass.enums.BizCodeEnum;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonData {

    /**
     * 状态码：0 表示成功
     */
    private Integer code;

    /**
     * 返回的数据
     */
    private Object data;

    /**
     * 错误信息描述
     */
    private String msg;


    /**
     *  【转换远程调用返回的数据至指定类型】
     *
     *  过程：
     *      返回的 JsonData 是 Object类型 ==> String类型 ==> 指定引用类型的对象
     *
     *  注意：
     *      支持多单词下划线专驼峰（序列化和反序列化）
     *
     * @param typeReference JsonData 要转换为该类型
     * @return <T> T 转换类型后的数据
     */
    public <T> T getData(TypeReference<T> typeReference){
        return JSON.parseObject(JSON.toJSONString(data), typeReference);
    }


    /**
     * 成功，不传入数据
     */
    public static JsonData buildSuccess() {
        return new JsonData(0, null, null);
    }

    /**
     * 成功，传入数据
     * @param data
     */
    public static JsonData buildSuccess(Object data) {
        return new JsonData(0, data, null);
    }

    /**
     * 失败，传入描述信息
     * @param msg
     */
    public static JsonData buildError(String msg) {
        return new JsonData(-1, null, msg);
    }

    /**
     * 自定义状态码和错误信息
     * @param code
     * @param msg
     */
    public static JsonData buildCodeAndMsg(int code, String msg) {
        return new JsonData(code, null, msg);
    }

    /**
     * 传入自定义的枚举，返回状态码和错误信息
     * @param codeEnum
     */
    public static JsonData buildResult(BizCodeEnum codeEnum){
        return JsonData.buildCodeAndMsg(codeEnum.getCode(),codeEnum.getMessage());
    }
}