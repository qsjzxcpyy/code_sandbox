package com.qsj.acojcodesandbox.utils;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.StrUtil;
import com.qsj.acojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessUtils {
    /**
     * 根据传入的进程得到进程执行的状态和信息
     *
     * @param runProcess //具体的进程
     * @param opName     //操作名称
     * @return
     */
    private static final long TIME_OUT = 5000L;

    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int runValue = runProcess.waitFor();
            executeMessage.setExitValue(runValue);
            if (runValue == 0) {
                System.out.println(opName + "成功");
                List<String> runOutputMessage = new ArrayList<>();
                String runOutputLine = null;
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                while ((runOutputLine = bufferedReader.readLine()) != null) {
                    runOutputMessage.add(runOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(runOutputMessage, "\n"));
            } else {
                System.out.println(opName + "失败， 错误码: " + runValue);
                String runOutputLine = null;
                List<String> runOutputMessage = new ArrayList<>();

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                while ((runOutputLine = bufferedReader.readLine()) != null) {
                    runOutputMessage.add(runOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(runOutputMessage, "\n"));

                String runOutputErrorLine = null;

                List<String> runOutputErrorMessage = new ArrayList<>();

                BufferedReader ErrorReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                while ((runOutputErrorLine = ErrorReader.readLine()) != null) {
                    runOutputErrorMessage.add(runOutputErrorLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(runOutputErrorMessage, "\n"));

            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());

        } catch (
                IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return executeMessage;
    }

    /**
     * 这种方法不起作用，以后解决
     *
     * @param runProcess
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        OutputStream outputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        List<String> outPutMessage = new ArrayList<>();

        try {
            // 向控制台输入程序
            outputStream = runProcess.getOutputStream();
            outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join = StrUtil.join("\n", s) + "\n";
            outputStreamWriter.write(join);
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();

            // 分批获取进程的正常输出
            inputStream = runProcess.getInputStream();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            // 逐行读取
            String OutputLine;
            while ((OutputLine = bufferedReader.readLine()) != null) {
                outPutMessage.add(OutputLine);
            }

            // 等待程序执行结束
            runProcess.waitFor();

            stopWatch.stop();
            long lastTaskTimeMillis = stopWatch.getLastTaskTimeMillis();
            executeMessage.setMessage(StringUtils.join(outPutMessage, " "));
            executeMessage.setTime(lastTaskTimeMillis);

        } catch (IOException e) {
            // Stream closed 异常不处理
            if (!e.getMessage().contains("Stream closed")) {
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            }
        } catch (Exception e) {
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            executeMessage.setMessage(StringUtils.join(outPutMessage, " "));
            executeMessage.setErrorMessage(e.getMessage());
        } finally {
            // 关闭所有资源
            try {
                if (outputStreamWriter != null) {
                    outputStreamWriter.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (runProcess != null && runProcess.isAlive()) {
                    runProcess.destroy();
                }
            } catch (IOException e) {
                // 忽略关闭资源时的异常
            }
        }
        return executeMessage;
    }


}