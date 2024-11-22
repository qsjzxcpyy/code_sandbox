package com.qsj.acojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.qsj.acojcodesandbox.model.Eums.ExecuteCodeMessageEum;
import com.qsj.acojcodesandbox.model.ExecuteCodeRequest;
import com.qsj.acojcodesandbox.model.ExecuteCodeResponse;
import com.qsj.acojcodesandbox.model.ExecuteMessage;
import com.qsj.acojcodesandbox.model.JudgeInfo;
import com.qsj.acojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Component
public class JavaNativeCodeSandbox implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 3103L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> inputList = executeCodeRequest.getIntputList();
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        Process compileProcess;
        try {
            compileProcess = Runtime.getRuntime().exec(compileCmd);
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        ExecuteMessage compileExecuteMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");

        if (StrUtil.isNotBlank(compileExecuteMessage.getErrorMessage())) {
            executeCodeResponse.setStatue(2);
            executeCodeResponse.setOutputList(new ArrayList<>());
            executeCodeResponse.setMessage(ExecuteCodeMessageEum.COMPILE_ERROR.getValue());
            executeCodeResponse.setJudgeInfo(new JudgeInfo());
            return executeCodeResponse;
        }

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArg : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main", userCodeParentPath);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                MemoryTracker memoryTracker = new MemoryTracker(runProcess);
                memoryTracker.start();

                ExecuteMessage runMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArg);

                memoryTracker.stop();
                if (memoryTracker.isTimeout()) {
                    runMessage.setTime(TIME_OUT);
                }
                runMessage.setMemory(memoryTracker.getMaxMemoryUsage());
                executeMessageList.add(runMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }

        List<String> outputList = new ArrayList<>();
        long time = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                executeCodeResponse.setStatue(3);
                if (executeMessage.getTime() >= TIME_OUT * 2) {
                    time = executeMessage.getTime();
                }
                break;
            }
            time = Math.max(time, executeMessage.getTime());
            maxMemory = Math.max(maxMemory, executeMessage.getMemory());
            outputList.add(executeMessage.getMessage());
        }

        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setStatue(1);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(time);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("文件删除 " + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatue(3);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    public class MemoryTracker {
        private final Process process;
        private long maxMemoryUsage = 0;
        private volatile boolean isRunning = true;
        private Thread monitorThread;
        private final SystemInfo systemInfo;
        private final OperatingSystem os;
        private Long processId = null;
        private final long startTime;
        private volatile boolean hasStartedMonitoring = false;
        private static final long TIMEOUT_MS = 3000; // 10秒超时
        private final long startExecuteTime;
        private boolean isTimeout = false;

        public MemoryTracker(Process process) {
            this.process = process;
            this.systemInfo = new SystemInfo();
            this.os = systemInfo.getOperatingSystem();
            this.startTime = System.currentTimeMillis();
            this.startExecuteTime = System.currentTimeMillis();
        }

        private void findJavaProcess() {
            // 获取所有Java进程
            List<OSProcess> processes = os.getProcesses();

            // 按启动时间倒序排序
            processes.sort((p1, p2) -> Long.compare(p2.getStartTime(), p1.getStartTime()));

            for (OSProcess process : processes) {
                String cmd = process.getCommandLine();
                // 找到最近启动的包含Main的Java进程
                if (cmd != null && cmd.contains("java") && cmd.contains("Main")
                        && process.getStartTime() >= startTime - 1000) { // 允许1秒的时间误差
                    processId = (long) process.getProcessID();
                    System.out.println("找到目标进程 - PID: " + processId
                            + ", 启动时间: " + process.getStartTime()
                            + ", 命令行: " + cmd);
                    return;
                }
            }
            System.out.println("未找到目标进程");
        }

        public void start() {
            try {
                Thread.sleep(50);
                findJavaProcess();
            } catch (Exception e) {
                System.out.println("初始化内存跟踪器失败: " + e.getMessage());
                return;
            }

            // 创建超时检查线程
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(TIMEOUT_MS);
                    if (process.isAlive()) {
                        System.out.println("程序运行超时，强制终止");
                        isTimeout = true;
                        process.destroy();
                        isRunning = false;
                    }
                } catch (InterruptedException e) {
                    // 忽略中断异常
                }
            });
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            monitorThread = new Thread(() -> {
                try {
                    if (processId != null) {
                        System.out.println("开始监控进程内存，进程ID: " + processId);
                        int retryCount = 0;

                        while (isRunning && process.isAlive() && retryCount < 10) {
                            // 检查是否超时
                            if (System.currentTimeMillis() - startExecuteTime > TIMEOUT_MS) {
                                System.out.println("执行时间超过限制，终止进程");
                                isTimeout = true;
                                process.destroy();
                                break;
                            }

                            try {
                                OSProcess osProcess = os.getProcess((int) (long) processId);
                                if (osProcess != null) {
                                    long currentMemory = osProcess.getResidentSetSize() / 1024;
                                    if (currentMemory > 0) {
                                        hasStartedMonitoring = true;
                                        System.out.println("当前物理内存使用: " + formatMemorySize(currentMemory));
                                        maxMemoryUsage = Math.max(maxMemoryUsage, currentMemory);
                                        retryCount = 0;
                                    } else {
                                        retryCount++;
                                    }
                                } else {
                                    retryCount++;
                                }
                                Thread.sleep(10);
                            } catch (Exception e) {
                                System.out.println("内存采样异常: " + e.getMessage());
                                retryCount++;
                            }
                        }

                        if (!hasStartedMonitoring) {
                            System.out.println("未能成功监控到进程内存使用");
                        }
                    } else {
                        System.out.println("无法获取进程ID，内存监控失败");
                    }
                } catch (Exception e) {
                    System.out.println("内存监控线程异常: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            monitorThread.setDaemon(true);
            monitorThread.start();
        }

        public void stop() {
            try {
                // 如果进程还在运行，强制终止
                if (process.isAlive()) {
                    process.destroy();
                    // 如果普通的destroy()不起作用，使用destroyForcibly()
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                }

                isRunning = false;
                if (monitorThread != null) {
                    monitorThread.join(1000);
                }
                System.out.println("内存监控结束，最大物理内存使用: " + formatMemorySize(maxMemoryUsage));
            } catch (Exception e) {
                System.out.println("停止内存监控时发生异常: " + e.getMessage());
            }
        }

        public long getMaxMemoryUsage() {
            return maxMemoryUsage;
        }

        private String formatMemorySize(long sizeInKB) {
            if (sizeInKB < 1024) {
                return sizeInKB + " KB";
            } else if (sizeInKB < 1024 * 1024) {
                return String.format("%.2f MB", sizeInKB / 1024.0);
            } else {
                return String.format("%.2f GB", sizeInKB / (1024.0 * 1024.0));
            }
        }

        public boolean isTimeout() {
            return isTimeout;
        }
    }

}