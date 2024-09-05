package com.qsj.acojcodesandbox;


import com.qsj.acojcodesandbox.model.ExecuteCodeRequest;
import com.qsj.acojcodesandbox.model.ExecuteCodeResponse;

public interface CodeSandbox {
    /**
     * 运行代码，返回结果
     * @param executeCodeRequest
     * @return
     */
     ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
