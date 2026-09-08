package hooks

import org.json.JSONArray
import org.json.JSONObject

import com.kms.katalon.core.exception.StepFailedException
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.mobile.keyword.internal.MobileDriverFactory
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import groovy.json.JsonOutput
import internal.GlobalVariable
import core.GlobalVariableConfig
import core.HttpRequest
import core.SafeActionsMobile
import core.SafeActionsWeb
import core.Util

class TestrailIntegration {
	//============================================================ CONFIG ============================================================//

	/**
	 * Muat properties TestRail dengan prioritas:
	 *   1. local.properties (gitignored, untuk lokal — plain text, tidak perlu enkripsi)
	 *   2. GlobalVariable.TESTRAIL_PROPERTIES (dari profile Katalon)
	 * Nilai kosong di file lokal diabaikan agar tidak menimpa GlobalVariable.
	 */
	private static Map<String, String> loadTestrailProperties() {
		Map<String, String> props = new HashMap<>(GlobalVariable.TESTRAIL_PROPERTIES)
		GlobalVariableConfig.loadLocalProperties().each { key, value ->
			props[key] = value
		}
		return props
	}

	//	Util.decryptKatalon -> walau nilai tidak di encrypt -> tetap aman
	private static final Map<String, String> testrail_properties = loadTestrailProperties()
	private static final String testrailUrl = Util.decryptKatalon(testrail_properties.get("TESTRAIL_URL"))

	// Cache Basic Auth sekali saat class load — hindari decrypt berulang di setiap request
	private static final String CACHED_AUTH = setBasicAuth(
	Util.decryptKatalon(testrail_properties.get("TESTRAIL_USERNAME")),
	Util.decryptKatalon(testrail_properties.get("TESTRAIL_PASSWORD")))

	// Status ID constants (sesuai TestRail)
	private static final int STATUS_PASSED = 1
	private static final int STATUS_FAILED = 5

	// Prefix API
	private static final String API_PREFIX = "/api/v2"

	//=========================================================== HEADERS ============================================================//
	// TestObjectProperty untuk skenario WS langsung (multipart attachment).
	private static final TestObjectProperty headerAccept = new TestObjectProperty("Accept", ConditionType.EQUALS, "*/*")
	private static final TestObjectProperty headerAcceptEncoding = new TestObjectProperty("Accept-Encoding", ConditionType.EQUALS, "gzip, deflate, br")
	private static final TestObjectProperty headerConnection = new TestObjectProperty("Connection", ConditionType.EQUALS, "keep-alive")
	private static final TestObjectProperty headerAuthorization = new TestObjectProperty("Authorization", ConditionType.EQUALS, CACHED_AUTH)
	private static final TestObjectProperty headerContentTypeApplicationJson = new TestObjectProperty("Content-Type", ConditionType.EQUALS, "application/json")

	private static final List<TestObjectProperty> defaultHeaders = Arrays.asList(headerAccept, headerAcceptEncoding, headerConnection, headerAuthorization, headerContentTypeApplicationJson)

	// Map headers untuk HttpRequest.getRequest/postRequest
	private static Map<String, String> buildJsonHeadersMap() {
		return ['Accept': '*/*', 'Authorization': CACHED_AUTH]
	}

	//=========================================================== PUBLIC API =========================================================//

	static String setBasicAuth(String username, String password) {
		String valueToEncode = "${username}:${password}"
		return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes())
	}

	static String getProjectId(String projectName) {
		KeywordUtil.logInfo("[TestrailIntegration.getProjectId] START projectName=${projectName}")
		String endpoint = buildUrl("${API_PREFIX}/get_projects")

		def respObj = HttpRequest.getRequest(endpoint, [:], buildJsonHeadersMap(), '', 200)
		JSONObject json = new JSONObject(JsonOutput.toJson(respObj))
		JSONArray listProjects = json.getJSONArray("projects")

		for (int i = 0; i < listProjects.length(); i++) {
			if (listProjects.getJSONObject(i).get("name").toString().equalsIgnoreCase(projectName)) {
				return listProjects.getJSONObject(i).get("id").toString()
			}
		}
		throw new StepFailedException("[TestrailIntegration] Project '${projectName}' tidak ditemukan di TestRail.")
	}

	//GET TEST RUN ID
	static String getTestrunId(String testrunName, String configuration) {
		KeywordUtil.logInfo("[TestrailIntegration.getTestrunId] START testrunName=${testrunName} configuration=${configuration}")
		String projectId = getProjectId(testrail_properties.get("TESTRAIL_PROJECT_NAME"))
		String endpoint = buildUrl("${API_PREFIX}/get_runs/${projectId}")

		def respObj = HttpRequest.getRequest(endpoint, [:], buildJsonHeadersMap(), '', 200)
		JSONObject json = new JSONObject(JsonOutput.toJson(respObj))
		JSONArray listTestruns = json.getJSONArray("runs")

		for (int i = 0; i < listTestruns.length(); i++) {
			JSONObject run = listTestruns.getJSONObject(i)

			String runName = run.optString("name", null)
			String runConfig = run.isNull("config") ? null : run.optString("config", null)

			boolean nameMatch = runName != null && runName.equalsIgnoreCase(testrunName)

			// Kondisi 1: name sama && config sama
			boolean cond1 = (nameMatch && runConfig != null && configuration != null && runConfig.equalsIgnoreCase(configuration))

			// Kondisi 2: name sama && config null (test run tanpa konfigurasi)
			boolean cond2 = (nameMatch && runConfig == null)

			if (cond1 || cond2) {
				return listTestruns.getJSONObject(i).get("id").toString()
			}
		}

		throw new StepFailedException("[TestrailIntegration] Test run '${testrunName}' tidak ditemukan di TestRail.")
	}

	//GET TEST RUN ID IN TEST PLAN BY TEST PLAN ID
	static List<String> getTestrunIdByTestPlanId(String testPlanId, String testrunName, String configuration) {
		KeywordUtil.logInfo("[TestrailIntegration.getTestrunIdByTestPlanId] START testPlanId=${testPlanId} testrunName=${testrunName} configuration=${configuration}")
		String testPlanIdClean = testPlanId.replaceAll("[a-zA-Z\\s]", "")
		String endpoint = buildUrl("${API_PREFIX}/get_plan/${testPlanIdClean}")

		def respObj = HttpRequest.getRequest(endpoint, [:], buildJsonHeadersMap(), '', 200)
		JSONObject json = new JSONObject(JsonOutput.toJson(respObj))
		JSONArray entries = json.getJSONArray("entries")

		List<String> runIds = new ArrayList<>()

		for (int i = 0; i < entries.length(); i++) {
			JSONObject entry = entries.getJSONObject(i)
			JSONArray runs = entry.getJSONArray("runs")

			for (int j = 0; j < runs.length(); j++) {
				JSONObject run = runs.getJSONObject(j)

				String runName = run.optString("name", null)
				String runConfig = run.isNull("config") ? null : run.optString("config", null)

				boolean nameMatch = runName != null && runName.equalsIgnoreCase(testrunName)

				boolean cond1 = (nameMatch && runConfig != null && configuration != null &&
						runConfig.equalsIgnoreCase(configuration))

				boolean cond2 = (nameMatch && runConfig == null)

				if (cond1 || cond2) {
					runIds.add(run.get("id").toString())
					break
				}
			}
		}

		return runIds
	}

	//============================================================ RESULT ============================================================//
	//-- addTestrunResult - Just Test Run
	static String addTestrunResultForCase(String testcaseId, String testcaseStatus, String comment, String elapsed, String testrunName, String configuration) {
		KeywordUtil.logInfo("[TestrailIntegration.addTestrunResultForCase] START testcaseId=${testcaseId} status=${testcaseStatus} testrunName=${testrunName}")
		String cleanTestcaseId = cleanIdForCase(testcaseId)
		String testrunId = getTestrunId(testrunName, configuration)
		String endpoint = buildUrl("${API_PREFIX}/add_result_for_case/${testrunId}/${cleanTestcaseId}")
		String resultId = null

		if (!Util.isNullOrEmpty(testcaseId)) {
			Integer statusId = mapStatusId(testcaseStatus)
			if (statusId != null) {
				Map<String, Object> payload = [
					'status_id': statusId,
					'comment'  : comment ?: ''
				]
				if (elapsed != null) {
					payload['elapsed'] = elapsed
				}

				def respObj = HttpRequest.postRequest(endpoint, payload, true, buildJsonHeadersMap(), '', 200)
				// respObj adalah Map hasil parse JSON -> ambil id
				JSONObject json = new JSONObject(JsonOutput.toJson(respObj))
				resultId = json.get("id").toString()
			}
		}

		return resultId
	}

	//-- addTestrunResult - by Test Plan
	static String addTestrunResultForCaseByTestPlan(String testcaseId, String testcaseStatus, String comment, String elapsed, String testrunName, String configuration) {
		KeywordUtil.logInfo("[TestrailIntegration.addTestrunResultForCaseByTestPlan] START testcaseId=${testcaseId} status=${testcaseStatus} testrunName=${testrunName}")
		String testrunId = getTestrunIdByTestPlanId(testrail_properties.get("TESTRAIL_TEST_PLAN_ID"), testrunName, configuration)
				.toString().replaceAll("[a-zA-Z\\s\\[\\]\\r\\n]", "")
		String cleanTestcaseId = testcaseId.replaceAll("[a-zA-Z\\s\\[\\]\\r\\n]", "")
		String endpoint = buildUrl("${API_PREFIX}/add_result_for_case/${testrunId}/${cleanTestcaseId}")
		String resultId = null

		if (!Util.isNullOrEmpty(testcaseId)) {
			Integer statusId = mapStatusId(testcaseStatus)
			if (statusId != null) {
				// **Catatan**: Untuk method ini, logika asli melakukan handling khusus untuk kode 400 ("No (active) test found").
				JSONObject obj = new JSONObject()
				obj.put("status_id", statusId)
				obj.put("comment", comment != null ? comment : "")
				if (elapsed != null) {
					obj.put("elapsed", elapsed)
				}

				RequestObject ro = new RequestObject()
				ro.setRestUrl(endpoint)
				ro.setHttpHeaderProperties(defaultHeaders)
				ro.setRestRequestMethod("POST")
				ro.setBodyContent(new HttpTextBodyContent(obj.toString()))

				ResponseObject response = WS.sendRequest(ro)

				if (response.getStatusCode() == 200) {
					JSONObject json = new JSONObject(response.getResponseText())
					resultId = json.get("id").toString()
				} else if (response.getStatusCode() == 400 && response.getResponseText().contains("No (active) test found")) {
					KeywordUtil.logInfo("[WARN] Test case ID ${cleanTestcaseId} tidak ditemukan pada test run ${testrunId}. Melewati update result.")
				} else {
					KeywordUtil.markWarning("[addTestrunResult] Gagal mengirim hasil ke TestRail. Status code: ${response.getStatusCode()} | Response: ${response.getResponseText()}")
				}
			}
		}
		return resultId
	}

	//-- attachment
	static void addAttachmentForCaseResult(String testResultId, String testcaseName) {
		String endpoint = buildUrl("${API_PREFIX}/add_attachment_to_result/${testResultId}")

		File directoryPath = resolveScreenshotDirectory(testcaseName)
		String folderPath = directoryPath.path
		File[] fileList = directoryPath.listFiles()

		if (fileList == null || fileList.length == 0) {
			KeywordUtil.logInfo("[addAttachmentForCaseResult] Tidak ada file lampiran pada folder: ${folderPath}")
			return
		}

		Map<String, String> headers = ['Authorization': CACHED_AUTH]

		Map<String, String> fields = [:]

		fileList.each { File f ->
			if (f != null && f.isFile()) {
				try {
					Map<String, File> files = ['attachment': f]
					def resp = HttpRequest.postMultipart(endpoint, headers, '', fields, files, 200)
					KeywordUtil.logInfo("[ATTACHMENT] OK: ${f.name} -> ${resp}")
				} catch (StepFailedException sfe) {
					// Ini biasanya akibat expected 200 tapi server balas 4xx/5xx, contoh: disk penuh
					KeywordUtil.markWarning("[ATTACHMENT] Gagal upload ${f.name}: ${sfe.message}")
					// jangan rethrow; lanjut ke file berikutnya
				} catch (FileNotFoundException fnfe) {
					KeywordUtil.markWarning("[ATTACHMENT] File tidak ditemukan: ${f?.absolutePath} — ${fnfe.message}")
				} catch (Exception e) {
					KeywordUtil.markWarning("[ATTACHMENT] Error tak terduga pada ${f.name}: ${e.message}")
				}
			}
		}
	}

	//-- addAttachmentToRun - upload PNG files to a test run; returns LinkedHashMap<filename, attachment_id>
	static Map<String, String> addAttachmentToRun(String testrunId, String testcaseName) {
		String endpoint = buildUrl("${API_PREFIX}/add_attachment_to_run/${testrunId}")
		File directoryPath = resolveScreenshotDirectory(testcaseName)
		String folderPath = directoryPath.path
		File[] fileList = directoryPath.listFiles()
		Map<String, String> attachments = new LinkedHashMap<>()

		if (fileList == null || fileList.length == 0) {
			KeywordUtil.logInfo("[addAttachmentToRun] Tidak ada file lampiran pada folder: ${folderPath}")
			return attachments
		}

		Map<String, String> headers = ['Authorization': CACHED_AUTH]
		Map<String, String> fields = [:]

		// Susun daftar PNG diurutkan dari yang paling lama dimodifikasi
		List<File> filesToUpload = fileList
				.findAll { File f -> f != null && f.isFile() && f.name.toLowerCase().endsWith(".png") }
				.sort { it.lastModified() }

		// Jika ada error screenshot (prefix ERROR_), hanya upload file tersebut
		List<File> errorFiles = filesToUpload.findAll { it.name.startsWith("ERROR_") }
		if (errorFiles) filesToUpload = errorFiles

		filesToUpload.each { File f ->
			try {
				Map<String, File> files = ['attachment': f]
				def resp = HttpRequest.postMultipart(endpoint, headers, '', fields, files, 200)
				KeywordUtil.logInfo("[addAttachmentToRun] OK: ${f.name} -> ${resp}")
				JSONObject respJson = new JSONObject(JsonOutput.toJson(resp))
				Object attachmentIdObj = respJson.opt("attachment_id")
				if (attachmentIdObj != null) {
					String attachmentId = attachmentIdObj.toString()
					attachments.put(f.name, attachmentId)

					// Pindahkan file error ke folder arsip: EROR/<testcaseName>/<file>.png
					if (f.name.startsWith("ERROR_")) {
						try {
							File archiveDir = new File(resolveScreenshotDirectory("EROR"), relativeScreenshotPath(testcaseName))
							archiveDir.mkdirs()
							File archiveFile = new File(archiveDir, f.name)
							boolean moved = f.renameTo(archiveFile)
							if (moved) {
								KeywordUtil.logInfo("[addAttachmentToRun] File dipindahkan ke: ${archiveFile.absolutePath}")
							} else {
								KeywordUtil.markWarning("[addAttachmentToRun] Gagal memindahkan file: ${f.absolutePath}")
							}
						} catch (Exception moveEx) {
							KeywordUtil.markWarning("[addAttachmentToRun] Error saat memindahkan ${f.name}: ${moveEx.message}")
						}
					}
				} else {
					KeywordUtil.markWarning("[addAttachmentToRun] Respons upload tidak mengandung attachment_id untuk ${f.name}: ${resp}")
				}
			} catch (StepFailedException sfe) {
				KeywordUtil.markWarning("[addAttachmentToRun] Gagal upload ${f.name}: ${sfe.message}")
			} catch (FileNotFoundException fnfe) {
				KeywordUtil.markWarning("[addAttachmentToRun] File tidak ditemukan: ${f?.absolutePath} — ${fnfe.message}")
			} catch (Exception e) {
				KeywordUtil.markWarning("[addAttachmentToRun] Error tak terduga pada ${f.name}: ${e.message}")
			}
		}
		return attachments
	}

	// addTestrunResult to Testrail
	static void addTestrunResult(List<List<String>> testcaseProperties, String testrunName, String configuration) {
		KeywordUtil.logInfo("[addTestrunResult] Mulai proses: ${testcaseProperties}")

		testcaseProperties.each {
			KeywordUtil.logInfo("[addTestrunResult] Process entry: ${it}")
			if (it == null) {
				return
			}

			// 1. Tentukan testrunId
			String testrunId
			if (!Util.isNullOrEmpty(testrail_properties.get("TESTRAIL_TEST_PLAN_ID"))) {
				testrunId = getTestrunIdByTestPlanId(testrail_properties.get("TESTRAIL_TEST_PLAN_ID"), testrunName, configuration)
						.toString().replaceAll("[a-zA-Z\\s\\[\\]\\r\\n]", "")
			} else {
				testrunId = getTestrunId(testrunName, configuration)
			}

			// 2. Siapkan comment sebelum result dibuat
			String comment = "${it[0]}"
			String elapsed = null
			if (it.size() > 3 && it[3] != null) {
				long elapsedMs = it[3].toString().toLong()
				if (elapsedMs < 1000L) {
					elapsedMs = 1000L
				}
				long totalSeconds = elapsedMs.intdiv(1000L)
				long minutes = totalSeconds / 60
				long seconds = totalSeconds % 60
				elapsed = minutes > 0 ? "${minutes}m ${seconds}s" : "${seconds}s"
			}

			// 3. Buat result terlebih dahulu agar TestRail menyediakan result_id
			String resultId
			if (!Util.isNullOrEmpty(testrail_properties.get("TESTRAIL_TEST_PLAN_ID"))) {
				resultId = addTestrunResultForCaseByTestPlan("${it[1]}", "${it[2]}", comment, elapsed, testrunName, configuration)
			} else {
				resultId = addTestrunResultForCase("${it[1]}", "${it[2]}", comment, elapsed, testrunName, configuration)
			}

			// 4. Upload screenshot ke result yang baru dibuat
			if (!Util.isNullOrEmpty(resultId)) {
				addAttachmentForCaseResult(resultId, "${it[0]}")
			} else {
				KeywordUtil.markWarning("[addTestrunResult] Result ID kosong; attachment untuk ${it[0]} tidak di-upload.")
			}
		}
	}

	//=========================================================== PRIVATE HELPERS ====================================================//

	private static Integer mapStatusId(String testcaseStatus) {
		if (testcaseStatus == null) return null
		if (testcaseStatus.equalsIgnoreCase("PASSED")) return STATUS_PASSED
		if (testcaseStatus.equalsIgnoreCase("FAILED")) return STATUS_FAILED
		return null
	}

	// ID cleaning helper (menjaga pola regex sesuai kode asli)
	private static String cleanIdForCase(String s) {
		if (s == null) return ""
		return s.replaceAll("[a-zA-Z]", "")
				.replace(" ", "")
				.replace("\n", "")
				.replace("\r", "")
	}

	private static File resolveScreenshotDirectory(String screenshotPath) {
		String basePath = (GlobalVariable.SCREENSHOT_PATH?.toString()?.trim() ?: 'Screenshot')
		String requestedPath = relativeScreenshotPath(screenshotPath)
		File requestedDirectory = new File(requestedPath)
		if (requestedDirectory.isAbsolute()) return requestedDirectory

		String normalizedBase = basePath.replace('\\', '/').replaceAll('/+$', '')
		String normalizedRequested = requestedPath.replace('\\', '/').replaceAll('^/+|/+$', '')
		if (normalizedRequested == normalizedBase || normalizedRequested.startsWith("${normalizedBase}/")) {
			return new File(normalizedRequested)
		}
		return new File(basePath, requestedPath)
	}

	private static String relativeScreenshotPath(String screenshotPath) {
		String basePath = (GlobalVariable.SCREENSHOT_PATH?.toString()?.trim() ?: 'Screenshot')
		String normalizedBase = basePath.replace('\\', '/').replaceAll('/+$', '')
		String normalizedPath = (screenshotPath ?: '').toString().trim().replace('\\', '/').replaceAll('^/+|/+$', '')
		if (normalizedPath == normalizedBase) return ''
		if (normalizedPath.startsWith("${normalizedBase}/")) {
			return normalizedPath.substring(normalizedBase.length() + 1)
		}
		return screenshotPath
	}

	// Bangun URL yang aman (menjaga satu slash) dan sesuai format TestRail API
	// Bisa handle kedua format: dengan atau tanpa index.php?
	private static String buildUrl(String path) {
		if (Util.isNullOrEmpty(testrailUrl)) return path
		String base = testrailUrl.endsWith("/") ? testrailUrl.substring(0, testrailUrl.length() - 1) : testrailUrl
		String p = path.startsWith("/") ? path.substring(1) : path

		// Jika base sudah mengandung /index.php?, langsung gabung tanpa tambah lagi
		if (base.contains("/index.php?")) {
			return base + "/" + p
		}

		// Jika path adalah API path (api/v2/...), tambahkan /index.php?/ di depan
		if (p.startsWith("api/v2/")) {
			return base + "/index.php?/" + p
		}

		// Selain itu, langsung gabung dengan /
		return base + "/" + p
	}


	/**
	 * Menambahkan hasil TC ke list testcaseProperties untuk dikirim ke TestRail.
	 * @param testcaseProperties list hasil TC yang diakumulasikan
	 * @param name  nama test case
	 * @param id    TestRail case ID (format: "C12345")
	 * @param status "PASSED" atau "FAILED"
	 * @param elapsedTimeMs durasi eksekusi step dalam milidetik
	 */
	public static void addTC(List<List<String>> testcaseProperties, String name, String id, String status, String elapsedTimeMs = null) {
		KeywordUtil.logInfo("[TestrailIntegration.addTC] name=${name} id=${id} status=${status} elapsedTimeMs=${elapsedTimeMs}")
		if (elapsedTimeMs != null) {
			testcaseProperties.add([
				name,
				id,
				status,
				elapsedTimeMs
			])
		} else {
			testcaseProperties.add([name, id, status])
		}
	}

	private static boolean captureFailureScreenshot(String screenshotPath) {
		try {
			try {
				def mobileDriver = MobileDriverFactory.getDriver()
				if (mobileDriver != null) {
					SafeActionsMobile.safeScreenshot(screenshotPath)
					KeywordUtil.logInfo("[runStep] Screenshot error via SafeActionsMobile.safeScreenshot: ${screenshotPath}")
					return true
				}
			} catch (Throwable ignored) {
				KeywordUtil.logInfo("[runStep] Mobile driver tidak tersedia, memakai SafeActionsWeb.safeTakeScreenshot")
			}

			SafeActionsWeb.safeTakeScreenshot(screenshotPath)
			KeywordUtil.logInfo("[runStep] Screenshot error via SafeActionsWeb.safeTakeScreenshot: ${screenshotPath}")
			return true
		} catch (Throwable screenshotEx) {
			KeywordUtil.logInfo("[runStep] Gagal persiapkan screenshot error: ${screenshotEx.message}")
			return false
		}
	}

	/**
	 * Menjalankan satu step, merekam hasilnya ke TestRail, dan mengembalikan boolean
	 * tanpa menghentikan eksekusi test case.
	 *
	 * Catatan perubahan:
	 * - Sebelumnya menggunakan KeywordUtil.markWarning pada blok catch, sehingga ketika
	 *   action gagal (throw exception), status step tercatat WARNING (ikon kuning) dan
	 *   hasil test keseluruhan tetap PASSED — perilaku yang tidak sesuai ekspektasi.
	 * - Diganti menjadi KeywordUtil.markFailed (tanpa AndStop) agar status test menjadi
	 *   FAILED ketika ada step yang gagal, namun eksekusi tetap dilanjutkan ke step
	 *   berikutnya (tidak berhenti di tengah).
	 *
	 * @param testcaseProperties list untuk menampung hasil
	 * @param name  nama step / TC
	 * @param id    TestRail case ID
	 * @param action closure yang dieksekusi sebagai step
	 * @return true jika action berhasil, false jika gagal
	 */
	static boolean runStep(List<List<String>> testcaseProperties, String name, String id, Closure<?> action) {
		long startTimeMs = System.currentTimeMillis()
		try {
			action.call()
			long elapsedTimeMs = System.currentTimeMillis() - startTimeMs
			addTC(testcaseProperties, name, id, "PASSED", String.valueOf(elapsedTimeMs))
			KeywordUtil.logInfo("[runStep] PASSED: ${name} (${id}) elapsedTimeMs=${elapsedTimeMs}")
			return true
		} catch (Throwable t) {
			long elapsedTimeMs = System.currentTimeMillis() - startTimeMs
			// Ambil screenshot error seketika sebelum state layar berubah
			try {
				String safeId   = (id   ?: 'unknown').replaceAll(/[^\w\-]/, '_')
				String safeName = (name ?: 'unknown').replaceAll(/[^\w\s\-]/, '_').replaceAll(/\s+/, '_')
				String safeMsg  = (t.message ?: t.getClass().simpleName)
						.replaceAll(/[^\w\s\-]/, '_').replaceAll(/\s+/, '_').replaceAll(/_+$/, '')
				String timestamp = new Date().format('yyyyMMdd_HHmmssSSS')
				File screenshotDirectory = resolveScreenshotDirectory(name)
				String screenshotPath = new File(screenshotDirectory, "ERROR_${safeId}_${safeName}_${safeMsg}_${timestamp}.png").path
				screenshotDirectory.mkdirs()
				captureFailureScreenshot(screenshotPath)
				KeywordUtil.logInfo("[runStep] Screenshot error: ${screenshotPath}")
			} catch (Exception screenshotEx) {
				KeywordUtil.logInfo("[runStep] Gagal persiapkan screenshot error: ${screenshotEx.message}")
			}

			addTC(testcaseProperties, name, id, "FAILED", String.valueOf(elapsedTimeMs))

			// Cari frame paling relevan: bukan dari internal Java/Groovy/Katalon
			List<String> internalPrefixes = [
				'java.',
				'javax.',
				'sun.',
				'com.sun.',
				'groovy.',
				'org.codehaus.groovy.',
				'com.kms.',
				'com.google.',
				'org.openqa.',
				'io.appium.',
				'hooks.TestrailIntegration'
			]
			StackTraceElement relevantFrame = t.stackTrace.find { StackTraceElement frame ->
				!internalPrefixes.any { String prefix -> frame.className.startsWith(prefix) }
			}

			// Cari frame terluar dari paket flow user (bukan core/hooks)
			List<String> flowPrefixes = GlobalVariable.FLOW_PRE_FIXES //List package keyword yang akan dipakai
			List<StackTraceElement> flowFrames = t.stackTrace.findAll { StackTraceElement frame ->
				flowPrefixes.any { String prefix -> frame.className.startsWith(prefix) }
			}

			String location = ""
			if (relevantFrame) {
				location = " | lokasi: ${relevantFrame.className}.${relevantFrame.methodName}()" +
						" di ${relevantFrame.fileName} baris ${relevantFrame.lineNumber}"

				// Tampilkan rantai lengkap flow frames (hapus duplikat dengan relevantFrame di indeks 0)
				List<StackTraceElement> chainFrames = flowFrames
				if (chainFrames && chainFrames[0] == relevantFrame) {
					chainFrames = chainFrames.drop(1)
				}
				if (chainFrames) {
					location += " → dipanggil dari: " + chainFrames.collect { StackTraceElement frame ->
						"${frame.className}.${frame.methodName}() di ${frame.fileName} baris ${frame.lineNumber}"
					}.join(" → dipanggil dari: ")
				}
			}

			KeywordUtil.markFailed("[runStep] FAILED: ${name} (${id}) | ${t.message ?: t.toString()}${location}")
			return false
		}
	}
}