package com.qsj.acoj.judge.codesandbox;

import com.qsj.acoj.judge.codesandbox.model.ExecuteCodeRequest;
import com.qsj.acoj.judge.codesandbox.model.ExecuteCodeResponse;

    public interface CodeSandbox {
    /**
     * 运行代码，返回结果
     * @param executeCodeRequest
     * @return
     */
     ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
