package com.qsj.acoj.judge.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {
    /**
     * 执行结果
     */
    private List<String> outputList;
    /**
     * 接口信息，例如代码沙箱死机了
     */
    private String message;
    /**
     * 执行状态
     */
    private String statue;
    /**
     * 判题信息
     */
    private JudgeInfo judgeInfo;
}
