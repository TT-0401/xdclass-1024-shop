package net.xdclass.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectResult;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.OSSConfig;
import net.xdclass.service.FileService;
import net.xdclass.util.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private OSSConfig ossConfig;


    @Override
    public String uploadUserImg(MultipartFile file) {

        // 获取相关配置
        String bucketname = ossConfig.getBucketname();
        String endpoint = ossConfig.getEndpoint();
        String accessKeyId = ossConfig.getAccessKeyId();
        String accessKeySecret = ossConfig.getAccessKeySecret();

        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        // 获取传入文件的原始文件名，如 xxx.jpg
        String originalFileName = file.getOriginalFilename();

        // JDK8的日期格式
        LocalDateTime ldt = LocalDateTime.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        // 以时间命名文件夹
        String folder = dtf.format(ldt);
        // 随机生成文件名
        String fileName = CommonUtil.generateUUID();
        // 截取原文件的格式后缀
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));

        // 在OSS上的bucket下创建 user 文件夹
        // 拼装在oss上存储的路径，如 user/2022/12/1/UUID.jpg
        String newFileName = "user/" + folder + "/" + fileName + extension;

        try {
            // 上传文件至oss
            PutObjectResult putObjectResult = ossClient.putObject(bucketname,newFileName,file.getInputStream());
            // 拼装返回路径
            if(putObjectResult != null){
                String imgUrl = "https://" + bucketname + "." + endpoint + "/" + newFileName;
                return imgUrl;
            }
        } catch (IOException e) {
            log.error("文件上传失败:{}",e);
        } finally {
            //oss关闭服务，不然会造成OOM
            ossClient.shutdown();
        }

        return null;
    }
}
