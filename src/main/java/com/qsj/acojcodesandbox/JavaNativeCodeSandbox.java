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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class JavaNativeCodeSandbox implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 10000L;

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

                final ExecuteMessage[] runMessage = {ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArg)};

                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runProcess.isAlive()) {
                            runProcess.destroy();
                            runMessage[0].setTime(1000000L);
                            runMessage[0].setMemory(memoryTracker.getMaxMemoryUsage());
                            runMessage[0].setErrorMessage("运行超时");
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                memoryTracker.stop();
                runMessage[0].setMemory(memoryTracker.getMaxMemoryUsage());
                executeMessageList.add(runMessage[0]);

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
        private final Timer timer = new Timer();
        private final SystemInfo systemInfo = new SystemInfo();
        private final OperatingSystem os = systemInfo.getOperatingSystem();

        public MemoryTracker(Process process) {
            this.process = process;
        }

        public void start() {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        long pid = getProcessId();
                        maxMemoryUsage = Math.max(maxMemoryUsage, getMemoryUsage(pid));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 1000); // 每秒检查一次内存
        }

        public void stop() {
            timer.cancel();
        }

        public long getMaxMemoryUsage() {
            return maxMemoryUsage;
        }

        private long getProcessId() throws Exception {
            // JDK 8 的进程 ID 获取方式
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                return getProcessIdWindows(process);
            } else {
                return getProcessIdLinux(process);
            }
        }

        private  long getProcessIdWindows(Process process) throws Exception {
            // 通过 WMIC 命令获取 Windows 系统上的进程 ID
            Process taskListProcess = Runtime.getRuntime().exec("tasklist /FI \"IMAGENAME eq java.exe\"");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(taskListProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("java.exe")) {
                        String[] parts = line.trim().split("\\s+");
                        return Long.parseLong(parts[1]);
                    }
                }
            }
            throw new RuntimeException("无法确定进程ID.");
        }

        private  long getProcessIdLinux(Process process) throws Exception {
            // 通过 pgrep 命令获取 Linux 系统上的进程 ID
            Process pgrepProcess = Runtime.getRuntime().exec("pgrep -f java");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(pgrepProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    return Long.parseLong(line.trim());
                }
            }
            throw new RuntimeException("无法确定进程ID.");
        }

        private long getMemoryUsage(long pid) {
            // 使用 OSHI 获取进程内存使用情况
            OSProcess osProcess = os.getProcess((int) pid);
            if (osProcess != null) {
                // 返回 RSS 内存，以 KB 为单位
                return osProcess.getResidentSetSize() / 1024;
            }
            return 0;
        }
    }


}
