package com.qsj.acoj.judge.codesandbox.model;

import lombok.Data;

@Data
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;
    /**
     * 消耗的内存(KB)
     */
    private Long memory ;
    /**
     * 消耗的时间(ms)
     */
    private Long time;

}
