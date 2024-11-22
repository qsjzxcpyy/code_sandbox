package com.qsj.acojcodesandbox.controller;

import com.qsj.acojcodesandbox.JavaNativeCodeSandbox;
import com.qsj.acojcodesandbox.model.ExecuteCodeRequest;
import com.qsj.acojcodesandbox.model.ExecuteCodeResponse;
import javassist.tools.web.BadHttpRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@RestController("/")
public class MainController {
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Resource
    JavaNativeCodeSandbox javaNativeCodeSandbox;
    @Resource
    private RedissonClient redissonClient;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request
            , HttpServletResponse response) throws Exception {
        String secretKey = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(secretKey)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("参数为空");
        }
        RLock rLock = redissonClient.getLock(String.valueOf("sss".hashCode()));
        ExecuteCodeResponse executeCodeResponse = null;
        rLock.lock();
        try {

         executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        } catch (Throwable e) {
            throw new Exception(e);
        } finally {
            rLock.unlock();
        }


        return executeCodeResponse;

    }

}
