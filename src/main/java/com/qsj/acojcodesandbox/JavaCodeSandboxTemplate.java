package com.qsj.acojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import com.qsj.acojcodesandbox.model.ExecuteCodeRequest;
import com.qsj.acojcodesandbox.model.ExecuteCodeResponse;
import com.qsj.acojcodesandbox.model.ExecuteMessage;
import com.qsj.acojcodesandbox.model.JudgeInfo;
import com.qsj.acojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    public File saveFile(String code){
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //通过创建不同的文件夹存放代码实现用户代码的隔离

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;

        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    public ExecuteMessage compileFile(File userCodeFile){
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        Process compileProcess = null;
        ExecuteMessage compileExecuteMessage;
        try {
            compileProcess = Runtime.getRuntime().exec(compileCmd);

         compileExecuteMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if(compileExecuteMessage.getExitValue() != 0){
                throw new RuntimeException("编译错误");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return compileExecuteMessage;
    }


    public List<ExecuteMessage> runFile(File userCodeFile,List<String> inputList){
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        for (String inputArg : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArg);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage runMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runProcess.isAlive()) {
                            System.out.println("超时中断了");
                            runMessage.setMessage("超时");
                            runProcess.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                }).start();

                executeMessageList.add(runMessage);
                System.out.println(runMessage);
            } catch (IOException e) {
                throw new RuntimeException("程序执行错误",e);
            }


        }
        return executeMessageList;
    }
   public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> listExecuteMessage){
       ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
       List<String> outputList = new ArrayList<>();
       long time = 0;
       for (ExecuteMessage executeMessage : listExecuteMessage) {
           if (!StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
               executeCodeResponse.setMessage(executeMessage.getErrorMessage());
               executeCodeResponse.setStatue(3);//代码运行中的错误
               break;
           }
           time = Math.max(time, executeMessage.getTime());
           outputList.add(executeMessage.getMessage());
       }
       executeCodeResponse.setOutputList(outputList);
       // executeCodeResponse.setMessage();
       if(outputList.size() == listExecuteMessage.size()) {
           executeCodeResponse.setStatue(1);
       } else {
           executeCodeResponse.setStatue(3);
       }
       JudgeInfo judgeInfo = new JudgeInfo();
       judgeInfo.setTime(time);
//        judgeInfo.setMemory();

       executeCodeResponse.setJudgeInfo(judgeInfo);
       return executeCodeResponse;
   }
    public Boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParentFile() != null) {
            String absolutePath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(absolutePath);
            System.out.println("文件删除 " + (del ? "成功" : "失败"));
            return del;
        }
      return true;

    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();


        List<String> intputList = executeCodeRequest.getIntputList();
        String language = executeCodeRequest.getLanguage();
        //判断存放code的全局文件夹是否存在，不存在则创建
        File userCodeFile = saveFile(code);
        //编译代码获得编译输出结果
        compileFile(userCodeFile);

        //运行代码，得到输出信息和执行信息
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile,intputList);


        //整理输出结果

        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        //文件清理
        Boolean b = deleteFile(userCodeFile);
        if (!b) {
            log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
        }

        return outputResponse;
    }

        //出现异常，处理返回结果
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatue(2);//2表示编译错误，3 表示运行中发生的错误
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;

    }
}
