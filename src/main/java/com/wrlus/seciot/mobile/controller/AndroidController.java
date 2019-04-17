package com.wrlus.seciot.mobile.controller;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrlus.seciot.mobile.model.ApkInfo;
import com.wrlus.seciot.mobile.service.AndroidServiceImpl;
import com.wrlus.seciot.platform.model.PlatformRiskDao;
import com.wrlus.seciot.platform.model.PlatformRiskResult;
import com.wrlus.seciot.platform.service.PlatformRiskServiceImpl;
import com.wrlus.seciot.pysocket.model.PythonException;
import com.wrlus.seciot.util.OSUtil;
import com.wrlus.seciot.util.Status;

@Controller
@RequestMapping("/android")
public class AndroidController {
	private static Logger log = LogManager.getLogger();
	@Autowired
	private AndroidServiceImpl androidService;
	@Autowired
	private PlatformRiskServiceImpl platformRiskService;
	
	@ResponseBody
	@RequestMapping("/analysis")
	public Map<String, Object> analysis(HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> data=new HashMap<String, Object>();
		ObjectMapper mapper = new ObjectMapper();
		// Windows: file:/C:/******/SecIoT/WebContent/WEB-INF/classes/
		// *nix: file:/mnt/******/SecIoT/WEB-INF/classes/
		String path = Thread.currentThread().getContextClassLoader().getResource("").toString();
		System.out.println(path);
		if (OSUtil.isWindows()) {
			path = path.replace("file:/", "");
		} else {
			path = path.replace("file:", "");
		}
		path = path.replace("WEB-INF/classes/", "attach/uploads/apk/"+UUID.randomUUID().toString()+"/");
		if (OSUtil.isWindows()) {
			path = OSUtil.escapeUnixSeparator(path);
		}
		try {
//			保存上传文件
			File apkFile = this.resolveUploadFile((MultipartHttpServletRequest) request, path);
			ApkInfo apkInfo = androidService.getApkInfo(apkFile);
			log.debug("ApkInfo: " + mapper.writeValueAsString(apkInfo));
			String[] permissions = androidService.getAndroidPermissions(apkInfo);
			List<PlatformRiskDao> platformRisks = platformRiskService.getPlatformRiskByCategory("Android");
			List<PlatformRiskResult> platformRiskResults = androidService.checkApkPlatformRisks(apkInfo, platformRisks.toArray(new PlatformRiskDao[0]));;
//			清除绝对路径信息，防止路径泄露
			apkInfo.setManifestFile(apkInfo.getManifestFile().replace(apkInfo.getPath() + ".jdax.out", ""));
			apkInfo.setNdkLibPath(apkInfo.getNdkLibPath().replace(apkInfo.getPath() + ".jdax.out", ""));
			apkInfo.setResourcesPath(apkInfo.getResourcesPath().replace(apkInfo.getPath() + ".jdax.out", ""));
			apkInfo.setSourcesPath(apkInfo.getSourcesPath().replace(apkInfo.getPath() + ".jdax.out", ""));
			apkInfo.setPath("");
//			返回状态码
			data.put("status", Status.SUCCESS);
//			返回状态说明字符串
			data.put("reason", "OK");
//			返回APK信息
			data.put("apk_info", apkInfo);
//			返回APK所需权限
			data.put("apk_permissions", permissions);
//			返回包含的APK平台风险数量
			data.put("apk_platform_risk_size", platformRiskResults.size());
//			返回APK平台风险详情
			data.put("apk_platform_risk", platformRiskResults);
		} catch (ClassCastException | NullPointerException e) {
			data.put("status", Status.FILE_UPD_ERROR);
			data.put("reason", "文件上传失败，错误代码："+Status.FILE_UPD_ERROR);
			log.error(e.getClass().getName() + ": " + e.getLocalizedMessage());
			if (log.isDebugEnabled()) {
				e.printStackTrace();
			}
		} catch (PythonException e) {
			data.put("status", Status.PY_ERROR);
			data.put("reason", e.getLocalizedMessage());
			log.error(e.getClass().getName() + ": " + e.getLocalizedMessage());
			if (log.isDebugEnabled()) {
				e.printStackTrace();
			}
		} catch (IllegalStateException e) {
			data.put("status", Status.FILE_UPD_ERROR);
			data.put("reason", "Python出现异常，错误代码："+Status.PY_ERROR);
			log.error(e.getClass().getName() + ": " + e.getLocalizedMessage());
			if (log.isDebugEnabled()) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			data.put("status", Status.IO_ERROR);
			data.put("reason", "文件或Socket I/O错误，错误代码："+Status.IO_ERROR);
			log.error(e.getClass().getName() + ": " + e.getLocalizedMessage());
			if (log.isDebugEnabled()) {
				e.printStackTrace();
			}
		}
		return data;
	}
	
	private File resolveUploadFile(MultipartHttpServletRequest multipartRequest, String path) throws IllegalStateException, IOException{
		MultipartFile multipartFile = multipartRequest.getFile("file");
		new File(path).mkdirs();
		File targetFile = new File(path + multipartFile.getOriginalFilename());
		multipartFile.transferTo(targetFile);
		return targetFile;
	}
}
